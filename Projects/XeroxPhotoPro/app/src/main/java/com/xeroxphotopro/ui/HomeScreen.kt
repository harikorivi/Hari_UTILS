package com.xeroxphotopro.ui
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.xeroxphotopro.house.HouseScreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xeroxphotopro.smart.SmartApp

@Composable fun HomeScreen() {
    var workflow by rememberSaveable { mutableStateOf("home") }
    BackHandler(workflow!="home") {workflow="home"}
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if(workflow=="home") {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Text("Xerox Photo Pro",style=MaterialTheme.typography.headlineMedium)
                    Text("Choose your printing workflow")
                    Button(onClick={workflow="passport"},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {Text("Passport Photo Maker")}
                    Text("Face framing, background replacement and passport photo sheets.")
                    OutlinedButton(onClick={workflow="smart"},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {Text("Smart A4 Photo Layout Maker")}
                    Text("Any images, mixed print sizes, exact copies or Maximum Fit on one A4.")
                    OutlinedButton(onClick={workflow="house"},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {Text("Telangana House Scheme Photo Copy Format")}
                    Text("One, two or three landscape photo copies on portrait A4.")
                }
            } else {
                TextButton(onClick={workflow="home"},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Home · switch workflow")}
                Box(Modifier.weight(1f)) {when(workflow) {"passport"->PhotoApp();"house"->HouseScreen();else->SmartApp()}}
            }
        }
    }
}
