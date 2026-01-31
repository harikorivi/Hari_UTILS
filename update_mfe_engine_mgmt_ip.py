# ======================================================================
# WLST Script: update_mfe_engine_mgmt_ip.py
#
# Purpose:
#   Update the Management IP of a specific Media Flow Engine (MFE) instance
#   inside the Media Server custom resource. This script searches the WLST
#   tree under the mediaserver custom resource to locate the target engine
#   by its ServerName and updates its ManagementIP attribute.
#
# Why this script:
#   In this project, the custom resource is named "mediaserver"
#   (see config.xml <custom-resource><name>mediaserver</name>).
#   Therefore, the base WLST path should start at:
#     /CustomResources/mediaserver
#
# Usage:
#   wlst.sh tools/scripts/wlst/update_mfe_engine_mgmt_ip.py \
#     -adminUrl t3://admin-host:7001 -username weblogic -password ***** \
#     -engineName <ENGINE_SERVER_NAME> -mgmtIp 10.1.2.3 \
#     [-basePath /CustomResources/mediaserver] \
#     [-attrName ManagementIP] \
#     [-maxDepth 8] \
#     [-enginePath <absolute WLST path to the engine bean>]
#
# Examples:
#   Auto-discover the engine bean under mediaserver and update its IP:
#     wlst.sh tools/scripts/wlst/update_mfe_engine_mgmt_ip.py \
#       -adminUrl t3://admin:7001 -username weblogic -password secret \
#       -engineName MFE-1 -mgmtIp 10.1.2.3
#
#   If you know the exact WLST path for the engine bean, use -enginePath:
#     wlst.sh tools/scripts/wlst/update_mfe_engine_mgmt_ip.py \
#       -adminUrl t3://admin:7001 -username weblogic -password secret \
#       -enginePath "/CustomResources/mediaserver/MediaFlowEngines/MFE-1" \
#       -mgmtIp 10.1.2.3 -attrName ManagementIP
#
# Notes:
#   - This script runs ONLINE (connects to AdminServer).
#   - It performs a bounded breadth-first search under the base path to find
#     a node whose "ServerName" attribute equals -engineName, or whose path
#     ends with "/<engineName>".
#   - Once the engine node is found, it sets the desired attribute
#     (default "ManagementIP") to the provided IP value and activates the change.
#
# ======================================================================

import sys

def usage_and_exit():
    print("Usage: wlst.sh update_mfe_engine_mgmt_ip.py -adminUrl t3://host:port -username user -password pass "
          "-engineName <ENGINE_NAME> -mgmtIp x.x.x.x "
          "[-basePath /CustomResources/mediaserver] [-attrName ManagementIP] [-maxDepth 8] [-enginePath <absolute_path>]")
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

def normalize_path(p):
    if p is None:
        return None
    return p.rstrip("/")

def children_of(path_abs):
    try:
        cd(path_abs)
    except:
        return []
    try:
        m = ls(returnMap='true')
        if not m:
            return []
        # keys are child names (folders/beans). Filter out attributes if any appear.
        # WLST typically returns only child bean names here.
        return list(m.keys())
    except:
        return []

def has_server_name_eq(path_abs, engine_name):
    try:
        cd(path_abs)
        try:
            val = get('ServerName')
            if val == engine_name:
                return True
        except:
            pass
    except:
        pass
    return False

def bfs_find_engine(base_path, engine_name, max_depth):
    base_path = normalize_path(base_path)
    if not base_path:
        return None
    # Quick check on base path
    try:
        cd(base_path)
    except:
        return None
    # BFS
    from collections import deque
    q = deque()
    visited = set()
    q.append((base_path, 0))
    visited.add(base_path)
    while q:
        path, depth = q.popleft()
        # Check attribute match
        if has_server_name_eq(path, engine_name):
            return path
        # Check by tail name
        if path.endswith("/" + engine_name):
            return path
        if depth < max_depth:
            for child in children_of(path):
                child_path = path + "/" + child
                if child_path not in visited:
                    visited.add(child_path)
                    q.append((child_path, depth + 1))
    return None

# ----------------------------------------------------------------------
# Parse arguments
# ----------------------------------------------------------------------
adminUrl   = get_arg("-adminUrl")
username   = get_arg("-username")
password   = get_arg("-password")
engineName = get_arg("-engineName")
mgmtIp     = get_arg("-mgmtIp")

basePath   = get_arg("-basePath")  # default later
attrName   = get_arg("-attrName")  # default later
maxDepth   = get_arg("-maxDepth")
enginePath = get_arg("-enginePath")

if not adminUrl or not username or not password or not mgmtIp:
    usage_and_exit()

if enginePath is None and not engineName:
    print("ERROR: Either -enginePath or -engineName must be provided.")
    usage_and_exit()

if basePath is None:
    basePath = "/CustomResources/mediaserver"  # Derived from config.xml in this repo

if attrName is None:
    attrName = "ManagementIP"

try:
    maxDepth = int(maxDepth) if maxDepth else 8
except:
    maxDepth = 8

# ----------------------------------------------------------------------
# Connect and enter edit session
# ----------------------------------------------------------------------
connect(username, password, adminUrl)
serverConfig()  # ensure config tree

edit()
startEdit()

# ----------------------------------------------------------------------
# Locate the target engine node
# ----------------------------------------------------------------------
target_engine_path = None

if enginePath:
    # Use explicit path first
    p = normalize_path(enginePath)
    try:
        cd(p)
        target_engine_path = p
    except:
        safe_cancel_edit("invalid enginePath")
        raise Exception("Provided -enginePath does not exist or is not accessible: %s" % p)
else:
    # Discover by searching under basePath (or fallback candidates) for matching ServerName/name
    candidates = [
        normalize_path(basePath),
        "/CustomResources/MediaFlowEngine",
        "/CustomResources/MediaEngine",
        "/CustomResources/occas",
        "/CustomResources/VoiceAIGateway",
        "/CustomResources/oracle.occas.media",
        "/CustomResources/mfe",
        "/CustomResources/mediaserver",
    ]
    tried = []
    target_engine_path = None
    for bp in [p for p in candidates if p]:
        tried.append(bp)
        candidate = bfs_find_engine(bp, engineName, maxDepth)
        if candidate:
            basePath = bp
            target_engine_path = candidate
            break
    if target_engine_path is None:
        safe_cancel_edit("engine not found")
        raise Exception("Unable to locate Media Flow Engine node for engineName='%s'. Tried bases: %s (depth=%d)"
                        % (engineName, ", ".join(tried), maxDepth))

print("Found Media Flow Engine node at: %s" % target_engine_path)

# ----------------------------------------------------------------------
# Apply the update
# ----------------------------------------------------------------------
try:
    cd(target_engine_path)
    set(attrName, mgmtIp)
    print("Set %s = %s" % (attrName, mgmtIp))
except:
    safe_cancel_edit("failed to set attribute")
    raise Exception("Failed to set attribute '%s' at '%s'" % (attrName, target_engine_path))

# ----------------------------------------------------------------------
# Save and activate
# ----------------------------------------------------------------------
save()
activate()
print("Successfully updated %s to %s for engine at %s" % (attrName, mgmtIp, target_engine_path))

# Disconnect cleanly
try:
    disconnect()
except:
    pass
