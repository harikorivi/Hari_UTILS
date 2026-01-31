# ======================================================================
# WLST Script: update_mfe_mgmt_ip.py
#
# Purpose:
#   Update the Media Flow Engine (MFE) Management IP in WebLogic via WLST.
#
# Usage:
#   wlst.sh update_mfe_mgmt_ip.py \ 
#     -adminUrl t3://admin-host:7001 -username weblogic -password ***** \
#     -mgmtIp 10.1.2.3 \
#     [-mbeanPath /CustomResources/MediaFlowEngine/CustomResource/MediaFlowEngine] \
#     [-attrName ManagementIP]
#
# Notes:
#   - If -mbeanPath and -attrName are provided, the script sets that attribute directly.
#   - Otherwise, the script attempts common MBean paths and attribute/property patterns.
#   - Runs ONLINE (connects to AdminServer and edits the live domain configuration).
#
#   Example (auto-detect):
#     wlst.sh update_mfe_mgmt_ip.py -adminUrl t3://admin:7001 -username weblogic -password secret -mgmtIp 10.1.2.3
#
#   Example (explicit path/attribute - recommended if known):
#     wlst.sh update_mfe_mgmt_ip.py -adminUrl t3://admin:7001 -username weblogic -password secret \
#       -mgmtIp 10.1.2.3 -mbeanPath /CustomResources/MediaFlowEngine/CustomResource/MediaFlowEngine -attrName ManagementIP
#
# ======================================================================

from java.lang import System
import sys

def usage_and_exit():
    print("Usage: wlst.sh update_mfe_mgmt_ip.py -adminUrl t3://host:port -username user -password pass -mgmtIp x.x.x.x "
          "[-mbeanPath <path>] [-attrName <name>]")
    print("Examples:")
    print("  wlst.sh update_mfe_mgmt_ip.py -adminUrl t3://admin:7001 -username weblogic -password secret -mgmtIp 10.1.2.3")
    print("  wlst.sh update_mfe_mgmt_ip.py -adminUrl t3://admin:7001 -username weblogic -password secret "
          "-mgmtIp 10.1.2.3 -mbeanPath /CustomResources/MediaFlowEngine/CustomResource/MediaFlowEngine -attrName ManagementIP")
    sys.exit(1)

def get_arg(flag):
    if flag in sys.argv:
        idx = sys.argv.index(flag)
        if idx + 1 < len(sys.argv):
            return sys.argv[idx + 1]
    return None

def safe_cancel_edit(reason=None):
    try:
        cancelEdit(reason if reason else "canceled")
    except:
        pass

def find_existing_path(paths):
    for p in paths:
        try:
            cd(p)
            return p
        except:
            pass
    return None

def try_set_direct_attributes(attr_names, value):
    for name in attr_names:
        try:
            set(name, value)
            print("Set attribute %s = %s" % (name, value))
            return True
        except:
            pass
    return False

def try_set_property_by_paths(container_abs_path, prop_name, value):
    # Attempts common property structures under the given container path
    candidates = [
        "%s/Properties/Property/%s" % (container_abs_path, prop_name),
        "%s/Property/%s" % (container_abs_path, prop_name),
        "%s/Properties/%s" % (container_abs_path, prop_name)
    ]
    for p in candidates:
        try:
            cd(p)
            set("Value", value)
            print("Updated property at %s: Value = %s" % (p, value))
            return True
        except:
            pass
    # Try to create property if supported
    try:
        cd(container_abs_path + "/Properties")
        try:
            cmo.createProperty(prop_name)
            cd("Property/%s" % prop_name)
            set("Value", value)
            print("Created and set property %s = %s under %s" % (prop_name, value, container_abs_path))
            return True
        except:
            pass
    except:
        pass
    return False

# ----------------------------------------------------------------------
# Parse arguments
# ----------------------------------------------------------------------
adminUrl = get_arg("-adminUrl")
username = get_arg("-username")
password = get_arg("-password")
mgmtIp   = get_arg("-mgmtIp")
mbeanPath = get_arg("-mbeanPath")
attrName  = get_arg("-attrName")

if not adminUrl or not username or not password or not mgmtIp:
    usage_and_exit()

# ----------------------------------------------------------------------
# Connect and enter edit session
# ----------------------------------------------------------------------
connect(username, password, adminUrl)
serverConfig()  # ensure we're in the config tree

edit()
startEdit()

# ----------------------------------------------------------------------
# Resolve target MBean path
# ----------------------------------------------------------------------
if mbeanPath:
    try:
        cd(mbeanPath)
        targetPath = mbeanPath
    except:
        safe_cancel_edit("invalid mbean path")
        raise Exception("Provided -mbeanPath does not exist or is not accessible: %s" % mbeanPath)
else:
    candidatePaths = [
        "/CustomResources/MediaFlowEngine/CustomResource/MediaFlowEngine",
        "/CustomResources/MediaEngine/CustomResource/MediaEngine",
        "/CustomResources/mfe/CustomResource/mfe",
        "/CustomResources/oracle.occas.media/CustomResource/MediaFlowEngine",
        "/CustomResources/VoiceAIGateway/CustomResource/MediaFlowEngine",
        "/CustomResources/occas/CustomResource/MediaFlowEngine",
    ]
    targetPath = find_existing_path(candidatePaths)
    if targetPath is None:
        safe_cancel_edit("no changes")
        raise Exception("Unable to locate Media Flow Engine CustomResource MBean. Checked: %s" % ", ".join(candidatePaths))

print("Found Media Flow Engine CustomResource at: %s" % targetPath)

# Ensure we are at the target before setting anything
cd(targetPath)

# ----------------------------------------------------------------------
# Apply change
# ----------------------------------------------------------------------
success = False

if attrName:
    try:
        set(attrName, mgmtIp)
        print("Set attribute %s = %s" % (attrName, mgmtIp))
        success = True
    except:
        safe_cancel_edit("failed to set attribute")
        raise Exception("Failed to set attribute %s at %s" % (attrName, targetPath))
else:
    # Try common attribute names first
    attr_candidates = ["ManagementIP", "ManagementAddress", "MgmtIP", "MgmtAddress"]
    success = try_set_direct_attributes(attr_candidates, mgmtIp)

    # If direct attribute fails, try properties layouts
    if not success:
        # Try commonly used property containers under the same MBean
        # Most common: .../Properties/Property/ManagementIP (with attribute "Value")
        success = try_set_property_by_paths(targetPath, "ManagementIP", mgmtIp)

    # Fallback: sometimes name might be "ManagementAddress"
    if not success:
        success = try_set_property_by_paths(targetPath, "ManagementAddress", mgmtIp)

if not success:
    safe_cancel_edit("no applicable attribute/property")
    raise Exception("Failed to set Media Flow Engine Management IP. No known attribute/property pattern worked at %s" % targetPath)

# ----------------------------------------------------------------------
# Save and activate
# ----------------------------------------------------------------------
save()
activate()
print("Successfully updated Media Flow Engine Management IP to %s" % mgmtIp)

# Disconnect cleanly
try:
    disconnect()
except:
    pass
