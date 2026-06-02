package com.ragagent.model

import com.google.gson.annotations.SerializedName

data class Product(
    @SerializedName("productId") val productId: String,
    @SerializedName("title") val title: String,
    @SerializedName("brand") val brand: String,
    @SerializedName("category") val category: String,
    @SerializedName("subCategory") val subCategory: String?,
    @SerializedName("basePrice") val basePrice: Double,
    @SerializedName("imagePath") val imagePath: String?,
    @SerializedName("marketingDescription") val marketingDescription: String?
)
