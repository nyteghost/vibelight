package com.limelight.tv.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text

/** Feasibility spike only: proves Kotlin + Compose for TV build and render in the pinned toolchain. */
class TvComposeSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpikeScreen() }
    }
}

private val Bg = Color(0xFF060918)
private val Panel = Color(0xFF182137)
private val Accent2 = Color(0xFFA5B4FC)
private val TextColor = Color(0xFFE2E8F0)

@Composable
private fun SpikeScreen() {
    Column(Modifier.fillMaxSize().background(Bg).padding(48.dp)) {
        Text("Compose for TV feasibility", color = TextColor, fontSize = 36.sp)
        Spacer(Modifier.height(24.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items((1..12).toList()) { i ->
                Card(
                    onClick = {},
                    modifier = Modifier.size(240.dp, 135.dp),
                    shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
                    colors = CardDefaults.colors(containerColor = Panel),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Accent2))),
                    scale = CardDefaults.scale(focusedScale = 1.06f)
                ) {
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Game $i", color = TextColor, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
