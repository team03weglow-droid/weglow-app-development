package com.example.weglow.domain.repository

import com.example.weglow.domain.model.Product

interface CatalogRepository {
    suspend fun products(): Result<List<Product>>
}
