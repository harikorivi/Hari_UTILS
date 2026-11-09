package com.xeroxphotopro.house

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xeroxphotopro.core.*
import java.util.Locale
import kotlin.math.abs

@Composable fun HouseScreen(vm: HouseViewModel=viewModel()) {
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {it?.let(vm::importPhoto)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {it?.let(vm::save)}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {it?.let(vm::saveAll)}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Telangana House Scheme Photo Copy Format",style=MaterialTheme.typography.headlineSmall)
            Text("Original photograph · white A4 · print and cut")
            Button(onClick={gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text(if(vm.thumbnail==null) "Upload original photograph" else "Replace original photograph")}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (1..3).forEach {n->OutlinedButton(onClick={vm.select(n)},enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=52.dp)) {Text("$n ${if(n==1) "copy" else "copies"}"+if(vm.copies==n) " ✓" else "")}}
            }
            Text("Cutting outline",style=MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(CutStyle.BLACK to "Black",CutStyle.GRAY to "Light grey").forEach { (style,name)->OutlinedButton(onClick={vm.select(vm.copies,style)},enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(name+if(vm.cut==style) " ✓" else "")}}
            }
            val rect=vm.layout.placements.first().rect
            Text(if(vm.copies<3) "Each frame: 152.4 × 101.6 mm (6 × 4 inches)." else "Each frame: ${String.format(Locale.US,"%.2f × %.2f",rect.width,rect.height)} mm (smaller than 6 × 4 inches).",style=MaterialTheme.typography.titleSmall)
            Text("Three full 6 × 4-inch photos exceed A4 height. Three-copy mode uses the largest equal 6:4 frames with 5 mm printer margins and 3 mm gaps.",style=MaterialTheme.typography.bodySmall)
            vm.preview?.let {bitmap->
                BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                    val width=minOf(maxWidth,210.dp)
                    Image(bitmap.asImageBitmap(),"House scheme A4 preview",Modifier.width(width).height(width*297f/210f).border(1.dp,Color.LightGray))
                }
                if(abs(vm.sourceRatio-1.5)>.015) Text("This photo is not 6:4. The whole image is preserved with white space inside the frame; nothing is cropped or stretched.")
                Text("A4 portrait · 2480 × 3508 pixels · 300 DPI. The outline marks the finished frame size. Save all creates three separate JPG files using the selected outline.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=vm::clear,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Delete working photograph")}
            }
        }
        if(vm.busy) {Text("Preparing photo sheet…",Modifier.padding(horizontal=16.dp));LinearProgressIndicator(Modifier.fillMaxWidth())}
        Surface(shadowElevation=5.dp) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Print A4 · Actual Size / 100%",style=MaterialTheme.typography.labelLarge)
                Button(onClick={save.launch(vm.filename())},enabled=!vm.busy&&vm.preview!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Save selected JPG")}
                OutlinedButton(onClick={folder.launch(null)},enabled=!vm.busy&&vm.preview!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Save all 3 JPGs to folder")}
            }
        }
    }
    vm.message?.let { text->AlertDialog(onDismissRequest=vm::dismiss,title={Text("Photo sheets")},text={Text(text)},confirmButton={TextButton(onClick=vm::dismiss) {Text("OK")}}) }
}
