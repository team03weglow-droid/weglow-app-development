package com.example.weglow.data.repository

import com.example.weglow.R
import com.example.weglow.domain.model.HairstyleRecommendation
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.repository.HairstyleRepository

class InMemoryHairstyleRepository : HairstyleRepository {
    override fun recommendationsFor(gender: String?): HairstyleResult = if (gender == "Male") {
        HairstyleResult(
            traits = listOf("Symmetrical Cheekbones", "Tapered Jawline", "Proportional Forehead"),
            description = "Your balanced facial proportions and subtle cheekbone taper allow maximum versatility. Volumetric crowns, neat temple fades, and textured fringes effortlessly accentuate your natural symmetry.",
            recommendations = listOf(
                HairstyleRecommendation(96, "HIGH VOLUME", "Fringe Up", "Elongates facial symmetry and draws attention to the crown.", R.drawable.hairstyle_male_fringe_up),
                HairstyleRecommendation(92, "CLEAN EDGES", "Modern Undercut", "Sharp taper on sides accentuates strong jawline definition.", R.drawable.hairstyle_male_undercut),
                HairstyleRecommendation(89, "LOW MAINTENANCE", "Textured Crop", "Casual matte layered texture with subtle blunt fringe.", R.drawable.hairstyle_male_textured_crop),
                HairstyleRecommendation(88, "STRUCTURED", "Classic Pompadour", "Polished swept-back volume paired with subtle side taper.", R.drawable.hairstyle_male_pompadour),
            ),
        )
    } else {
        HairstyleResult(
            traits = listOf("Symmetrical Cheekbones", "Soft Tapered Jaw", "Balanced Proportions"),
            description = "Your balanced proportions and gentle cheekbone contours allow maximum styling flexibility. Soft curtain layers, textured lobs, and voluminous butterfly cuts effortlessly highlight your natural beauty.",
            recommendations = listOf(
                HairstyleRecommendation(97, "HIGH VOLUME", "Butterfly Cut", "Cascading face-framing layers enhance natural movement.", R.drawable.hairstyle_female_butterfly_cut),
                HairstyleRecommendation(94, "BALANCED WAVES", "Curtain Bangs Bob", "Gentle parted fringe with airy textured ends.", R.drawable.hairstyle_female_curtain_bangs),
                HairstyleRecommendation(91, "PARISIAN CHIC", "Textured French Lob", "Collarbone-grazing soft waves provide effortless texture.", R.drawable.hairstyle_female_french_lob),
                HairstyleRecommendation(88, "MODERN EDGE", "Soft Layered Shag", "Feathered crown volume with wispy micro-fringe.", R.drawable.hairstyle_female_layered_shag),
            ),
        )
    }
}
