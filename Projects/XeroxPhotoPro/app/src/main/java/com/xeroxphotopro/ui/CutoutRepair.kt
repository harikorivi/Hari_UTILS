package com.xeroxphotopro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import com.xeroxphotopro.image.RepairCodec
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xeroxphotopro.PhotoViewModel
import com.xeroxphotopro.core.*
import kotlinx.coroutines.CancellationException
import kotlin.math.min

/** Draft edits are local until Apply; cancel never changes the exported portrait. */
@Composable
fun CutoutRepair(vm: PhotoViewModel, close: () -> Unit) {
    // Use the activity's visible frame: Dialog insets can inherit consumed parent insets.
    val visibleFrame=android.graphics.Rect().also { LocalView.current.getWindowVisibleDisplayFrame(it) }
    val editorHeight=with(LocalDensity.current) { visibleFrame.height().coerceAtLeast(1).toDp() }
    val original=vm.originalPrepared ?: return
    val crop=vm.crop ?: return
    val source=vm.photo ?: return
    var edits by rememberSaveable(stateSaver=Saver<List<MaskStroke>,String>(save={RepairCodec.encode(it)},restore={RepairCodec.decode(it)})) { mutableStateOf(vm.repairs) }
    var preview by remember { mutableStateOf(vm.prepared ?: original) }
    var mode by remember { mutableStateOf("Restore") }
    var before by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var radius by remember { mutableFloatStateOf(.02f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var pending by remember { mutableStateOf<List<BrushPoint>>(emptyList()) }
    var rendering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(edits) {
        rendering=true; error=null
        try { preview=vm.repairPreview(edits) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { error=e.message ?: "Unable to preview repair." }
        finally { rendering=false }
    }
    val scale=if(viewport.width==0) 1f else min(viewport.width.toFloat()/original.width,viewport.height.toFloat()/original.height)*zoom
    val origin=Offset((viewport.width-original.width*scale)/2+pan.x,(viewport.height-original.height*scale)/2+pan.y)
    val currentOrigin by rememberUpdatedState(origin)
    fun point(position: Offset): BrushPoint? {
        val x=(position.x-currentOrigin.x)/(original.width*scale)
        val y=(position.y-currentOrigin.y)/(original.height*scale)
        if(x !in 0f..1f || y !in 0f..1f) return null
        return BrushPoint(((crop.x+x*crop.width)/source.width).coerceIn(0.0,1.0),((crop.y+y*crop.height)/source.height).coerceIn(0.0,1.0))
    }
    fun finish() {
        if(pending.isNotEmpty() && edits.size<64) edits=edits+MaskStroke(pending,
            (radius*crop.width/min(source.width,source.height)).coerceIn(.002,.1),mode=="Restore")
        pending=emptyList()
    }
    @Composable fun heading() {
                Text("Repair ears / edges",style=MaterialTheme.typography.titleLarge)
                Text("Restore the original ear with short strokes. Release to preview. Use Erase for background spill.",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Restore","Erase","Move").forEach { tool ->
                        FilterChip(selected=mode==tool,onClick={mode=tool;before=false},enabled=!vm.busy,
                            modifier=Modifier.weight(1f).heightIn(min=48.dp),label={Text(tool)})
                    }
                }
    }
    @Composable fun paint(modifier: Modifier) {
                Canvas(modifier.clipToBounds().background(Color(0xFFE5E7EB)).onSizeChanged { viewport=it }
                    .semantics { contentDescription = "Repair canvas" }
                    .pointerInput(mode,zoom,viewport,before,edits.size,vm.busy) {
                        detectDragGestures(
                            onDragStart={ if(!before && !vm.busy && mode!="Move" && edits.size<64) pending=listOfNotNull(point(it)) },
                            onDragEnd={finish()}, onDragCancel={pending=emptyList()},
                            onDrag={ change,amount ->
                                change.consume()
                                if(!before && !vm.busy) {
                                    if(mode=="Move") pan=Offset((pan.x+amount.x).coerceIn(-viewport.width.toFloat(),viewport.width.toFloat()),(pan.y+amount.y).coerceIn(-viewport.height.toFloat(),viewport.height.toFloat()))
                                    else if(pending.size<256 && edits.size<64) point(change.position)?.let { pending=pending+it }
                                }
                            })
                    }) {
                    val canvas=drawContext.canvas.nativeCanvas
                    val save=canvas.save()
                    canvas.translate(origin.x,origin.y);canvas.scale(scale,scale)
                    canvas.drawBitmap(if(before) original else preview,0f,0f,android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    canvas.restoreToCount(save)
                    pending.lastOrNull()?.let { p ->
                        val center=Offset(origin.x+((p.u*source.width-crop.x)/crop.width*original.width*scale).toFloat(),origin.y+((p.v*source.height-crop.y)/crop.height*original.height*scale).toFloat())
                        drawCircle(if(mode=="Restore") Color.Green else Color.Red,original.width*scale*radius,center,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                }
    }
    @Composable fun controls() {
                if(rendering || vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                Text(if(before) "Original view · painting paused" else "${edits.size}/64 strokes · $mode",style=MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={before=!before},enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text(if(before) "Show repair" else "Compare original") }
                    TextButton(onClick={edits=edits.dropLast(1)},enabled=edits.isNotEmpty()&&!vm.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("Undo stroke") }
                    TextButton(onClick={edits=emptyList()},enabled=edits.isNotEmpty()&&!vm.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("Reset repair") }
                }
                Text("Zoom · use Move to reach each ear",style=MaterialTheme.typography.labelSmall)
                Slider(value=zoom,onValueChange={zoom=it;if(it==1f)pan=Offset.Zero},valueRange=1f..4f,enabled=!vm.busy)
                Text("Brush size",style=MaterialTheme.typography.labelSmall)
                Slider(value=radius,onValueChange={radius=it},valueRange=.005f.. .06f,enabled=!vm.busy)
                Text("Ear outside the frame? Cancel, then zoom out in Custom crop.",style=MaterialTheme.typography.bodySmall)
    }
    @Composable fun actions() {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=close,enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=52.dp)) { Text("Cancel") }
                    Button(onClick={vm.applyRepairs(edits,close)},enabled=!vm.busy&&!rendering&&error==null,modifier=Modifier.weight(1f).heightIn(min=52.dp)) { Text("Apply repair") }
                }
    }
    Dialog(onDismissRequest={ if(!vm.busy) close() }, properties=DialogProperties(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxWidth().height(editorHeight)) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                val controlsHeight=maxHeight*.45f
                if(maxWidth>maxHeight) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        paint(Modifier.weight(1f).fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) { heading();controls();actions() }
                    }
                } else {
                    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        heading();paint(Modifier.fillMaxWidth().weight(1f))
                        Column(Modifier.fillMaxWidth().heightIn(max=controlsHeight).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) { controls() }
                        actions()
                    }
                }
            }
        }
    }
}
