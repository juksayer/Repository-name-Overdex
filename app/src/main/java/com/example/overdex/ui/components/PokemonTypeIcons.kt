package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.PokemonType

enum class TypeIconStyle {
    GBA,
    OVERDEX
}

@Composable
fun PokemonTypeIcon(
    type: PokemonType,
    modifier: Modifier = Modifier,
    style: TypeIconStyle = TypeIconStyle.OVERDEX
) {
    when (style) {
        TypeIconStyle.GBA -> GBATypeBadge(type, modifier)
        TypeIconStyle.OVERDEX -> OverdexTypeIcon(type, modifier)
    }
}

@Composable
private fun GBATypeBadge(type: PokemonType, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(type.color, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = type.name.uppercase(),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun OverdexTypeIcon(type: PokemonType, modifier: Modifier) {
    Icon(
        imageVector = getOverdexIconForType(type),
        contentDescription = type.name,
        tint = type.color,
        modifier = modifier.size(16.dp)
    )
}

fun getOverdexIconForType(type: PokemonType): ImageVector {
    return when (type) {
        PokemonType.NORMAL -> IconNormal
        PokemonType.FIRE -> IconFire
        PokemonType.WATER -> IconWater
        PokemonType.ELECTRIC -> IconElectric
        PokemonType.GRASS -> IconGrass
        PokemonType.ICE -> IconIce
        PokemonType.FIGHTING -> IconFighting
        PokemonType.POISON -> IconPoison
        PokemonType.GROUND -> IconGround
        PokemonType.FLYING -> IconFlying
        PokemonType.PSYCHIC -> IconPsychic
        PokemonType.BUG -> IconBug
        PokemonType.ROCK -> IconRock
        PokemonType.GHOST -> IconGhost
        PokemonType.DRAGON -> IconDragon
        PokemonType.STEEL -> IconSteel
        PokemonType.FAIRY -> IconFairy
        PokemonType.DARK -> IconDark
    }
}

private fun typeIcon(name: String, block: ImageVector.Builder.() -> Unit) =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

private val IconNormal = typeIcon("Normal") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(4f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 20f)
        lineTo(4f, 20f)
        close()
        
        moveTo(12f, 12f)
        lineTo(12.1f, 12f)
    }
}

private val IconFire = typeIcon("Fire") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(12f, 20f)
        lineTo(12f, 4f)
        moveTo(12f, 20f)
        lineTo(6f, 8f)
        moveTo(12f, 20f)
        lineTo(18f, 8f)
    }
}

private val IconWater = typeIcon("Water") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(4f, 8f)
        curveTo(6f, 6f, 8f, 10f, 10f, 8f)
        curveTo(12f, 6f, 14f, 10f, 16f, 8f)
        curveTo(18f, 6f, 20f, 10f, 22f, 8f)
        
        moveTo(2f, 12f)
        curveTo(4f, 10f, 6f, 14f, 8f, 12f)
        curveTo(10f, 10f, 12f, 14f, 14f, 12f)
        curveTo(16f, 10f, 18f, 14f, 20f, 12f)
        
        moveTo(4f, 16f)
        curveTo(6f, 14f, 8f, 18f, 10f, 16f)
        curveTo(12f, 14f, 14f, 18f, 16f, 16f)
        curveTo(18f, 14f, 20f, 18f, 22f, 16f)
    }
}

private val IconElectric = typeIcon("Electric") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(18f, 4f)
        lineTo(8f, 12f)
        lineTo(16f, 12f)
        lineTo(6f, 20f)
    }
}

private val IconGrass = typeIcon("Grass") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(12f, 20f)
        lineTo(12f, 4f)
        moveTo(12f, 12f)
        lineTo(18f, 6f)
    }
}

private val IconIce = typeIcon("Ice") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(12f, 4f)
        lineTo(12f, 20f)
        moveTo(5f, 8f)
        lineTo(19f, 16f)
        moveTo(19f, 8f)
        lineTo(5f, 16f)
    }
}

private val IconFighting = typeIcon("Fighting") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(6f, 6f)
        lineTo(12f, 14f)
        lineTo(18f, 6f)
        moveTo(4f, 18f)
        lineTo(20f, 18f)
    }
}

private val IconPoison = typeIcon("Poison") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(8f, 16f)
        lineTo(12f, 8f)
        lineTo(16f, 16f)
        lineTo(8f, 16f)
    }
}

private val IconGround = typeIcon("Ground") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(4f, 8f)
        lineTo(20f, 8f)
        moveTo(7f, 13f)
        lineTo(17f, 13f)
        moveTo(10f, 18f)
        lineTo(14f, 18f)
    }
}

private val IconFlying = typeIcon("Flying") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(4f, 10f)
        lineTo(8f, 6f)
        moveTo(10f, 10f)
        lineTo(14f, 6f)
        moveTo(16f, 10f)
        lineTo(20f, 6f)
        moveTo(4f, 16f)
        lineTo(20f, 16f)
    }
}

private val IconPsychic = typeIcon("Psychic") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(12f, 12f)
        lineTo(12.1f, 12f)
        
        moveTo(15f, 9f)
        curveTo(17f, 11f, 17f, 13f, 15f, 15f)
        moveTo(18f, 6f)
        curveTo(21f, 9f, 21f, 15f, 18f, 18f)
    }
}

private val IconBug = typeIcon("Bug") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(12f, 4f)
        lineTo(12f, 20f)
        moveTo(8f, 8f)
        lineTo(16f, 8f)
        moveTo(8f, 12f)
        lineTo(16f, 12f)
        moveTo(8f, 16f)
        lineTo(16f, 16f)
    }
}

private val IconRock = typeIcon("Rock") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(12f, 4f)
        lineTo(20f, 8f)
        lineTo(20f, 16f)
        lineTo(12f, 20f)
        lineTo(4f, 16f)
        lineTo(4f, 8f)
        close()
        moveTo(12f, 4f)
        lineTo(12f, 20f)
    }
}

private val IconDragon = typeIcon("Dragon") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(12f, 8f)
        lineTo(20f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(12f, 8f)
        lineTo(12f, 4f)
        moveTo(10f, 4f)
        lineTo(14f, 4f)
    }
}

private val IconGhost = typeIcon("Ghost") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(12f, 4f)
        lineTo(12f, 12f)
        lineTo(16f, 12f)
        lineTo(16f, 20f)
    }
}

private val IconDark = typeIcon("Dark") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 3f) {
        // Draw a circle
        moveTo(12f, 4f)
        curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f)
        curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
        curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
        curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
        close()
        
        moveTo(12f, 12f)
        lineTo(12.1f, 12f)
    }
}

private val IconSteel = typeIcon("Steel") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        moveTo(4f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(4f, 4f)
        lineTo(20f, 20f)
        moveTo(20f, 4f)
        lineTo(4f, 20f)
    }
}

private val IconFairy = typeIcon("Fairy") {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
        // A circle
        moveTo(12f, 8f)
        curveTo(14.2f, 8f, 16f, 9.8f, 16f, 12f)
        curveTo(16f, 14.2f, 14.2f, 16f, 12f, 16f)
        curveTo(9.8f, 16f, 8f, 14.2f, 8f, 12f)
        curveTo(8f, 9.8f, 9.8f, 8f, 12f, 8f)
        close()
        
        // Ticks
        moveTo(17f, 17f)
        lineTo(20f, 20f)
        moveTo(7f, 7f)
        lineTo(4f, 4f)
        moveTo(17f, 7f)
        lineTo(20f, 4f)
        moveTo(7f, 17f)
        lineTo(4f, 20f)
    }
}
