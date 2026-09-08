package com.example.weglow.data.repository

import com.example.weglow.R
import com.example.weglow.domain.model.Product
import com.example.weglow.domain.repository.CatalogRepository

/** Prototype data source behind a repository seam. Supabase can replace this without UI changes. */
class InMemoryCatalogRepository : CatalogRepository {
    override fun products(): List<Product> = listOf(
        Product("ceylon-body-butter", "Ceylon Mango & Neroli Body Butter", "LKR 4200", "4.9", R.drawable.ceylon_mango_neroli_body_butter),
        Product("white-jasmine-scrub", "White Jasmine Facial Scrub", "LKR 3600", "4.8", R.drawable.white_jasmine_facial_serum),
        Product("sleep-balm", "Sleep Intense Dream Balm", "LKR 2800", "4.9", R.drawable.sleep_intense_dream_balm),
        Product("aloe-gel", "Aloe 95% Soothing Gel", "LKR 2400", "4.7", R.drawable.aloe_95_soothing_gel),
        Product("neem-wash", "Himalaya Neem Face Wash", "LKR 1800", "4.6", R.drawable.himalaya_neem_face_wash),
        Product("lavender-mist", "Lavender Sleeping Mist", "LKR 3200", "4.9", R.drawable.lavender_sleeping_mist),
        Product("chamomile-cream", "Chamomile Night Cream", "LKR 5400", "4.8", R.drawable.chamomile_night_cream),
        Product("matcha-foam", "Matcha Cleansing Foam", "LKR 2600", "4.7", R.drawable.matcha_cleansing_foam),
    )
}
