package com.example.weglow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.weglow.ui.theme.*

private data class DemoRoutineProduct(val step: String, val name: String, val size: String, val shape: Int)

private val demoCleanser = DemoRoutineProduct("Cleanser", "Gentle Daily Cleanser", "150 ml", 0)
private val demoMoisturizer = DemoRoutineProduct("Moisturizer", "Daily Moisture Cream", "50 ml", 1)
private val demoSunscreen = DemoRoutineProduct("Sunscreen", "Daily Sunscreen", "50 ml", 2)

/** Temporary UI-only preview: never creates catalog, schedule or completion records. */
@Composable
fun DemoRoutine(isMorning: Boolean) {
    val products = if (isMorning) listOf(demoCleanser, demoMoisturizer, demoSunscreen)
        else listOf(demoCleanser, demoMoisturizer)
    var checked by remember(isMorning) { mutableStateOf(emptySet<Int>()) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PageBackground).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Demo routine", style = MaterialTheme.typography.titleSmall, color = DarkGreen)
        Text("Sample products and steps for preview only. Checkmarks are temporary and do not affect your progress score.",
            style = MaterialTheme.typography.bodySmall, color = SoftGray)
        Text("${checked.size} of ${products.size} demo steps checked",
            style = MaterialTheme.typography.labelMedium, color = DarkGreen)
        products.forEachIndexed { index, product ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardWhite).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                DemoProductArtwork(product.shape, product.name)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("STEP ${index + 1} · ${product.step}", style = MaterialTheme.typography.labelSmall, color = SoftGray)
                    Text(product.name, style = MaterialTheme.typography.bodyMedium, color = DarkGreen)
                    Text("Demo product · ${product.size}", style = MaterialTheme.typography.labelSmall, color = SoftGray)
                }
                Checkbox(
                    modifier = Modifier.semantics { contentDescription = "Check demo ${product.name}" },
                    checked = index in checked,
                    onCheckedChange = { value -> checked = if (value) checked + index else checked - index },
                    colors = CheckboxDefaults.colors(checkedColor = DarkGreen),
                )
            }
        }
    }
}

/** Neutral sample packaging drawn locally; no external image or real brand is used. */
@Composable
private fun DemoProductArtwork(shape: Int, name: String) {
    Canvas(Modifier.size(width = 54.dp, height = 70.dp).clip(RoundedCornerShape(10.dp))
        .background(MintChip).semantics { contentDescription = "$name sample packaging" }) {
        val w = size.width
        val h = size.height
        val left = if (shape == 1) .12f else .23f
        val top = if (shape == 1) .48f else .25f
        val bodyWidth = if (shape == 1) .76f else .54f
        val bodyHeight = if (shape == 1) .35f else .57f
        val bodyColor = if (shape == 2) Color(0xFFE9D7A7) else Color(0xFFF9FAF5)
        drawRoundRect(bodyColor, Offset(w * left, h * top), Size(w * bodyWidth, h * bodyHeight), CornerRadius(w * .07f))
        if (shape == 0) {
            drawRect(DarkGreen, Offset(w * .42f, h * .16f), Size(w * .16f, h * .1f))
            drawRoundRect(DarkGreen, Offset(w * .35f, h * .12f), Size(w * .38f, h * .05f), CornerRadius(w * .02f))
        } else {
            val capTop = if (shape == 1) .4f else .82f
            drawRoundRect(DarkGreen, Offset(w * left, h * capTop), Size(w * bodyWidth, h * .09f), CornerRadius(w * .02f))
        }
        val labelY = if (shape == 1) .6f else .46f
        drawRect(DarkGreen.copy(alpha = .7f), Offset(w * .34f, h * labelY), Size(w * .32f, h * .035f))
        drawRect(DarkGreen.copy(alpha = .35f), Offset(w * .38f, h * (labelY + .07f)), Size(w * .24f, h * .02f))
    }
}
