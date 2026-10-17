package com.z.grow.data

/** Suggestions for the manufacturer and strain fields. Users can always type their own. */
object Catalog {
    const val GENERIC = "Generic"

    private val cannabisBreeders = listOf(
        "FastBuds",
        "Mephisto Genetics",
        "Night Owl Seeds",
        "Dutch Passion",
        "Royal Queen Seeds",
        "Barney's Farm",
        "Sensi Seeds",
        "Humboldt Seed Company",
        "Seedsman",
        "Sweet Seeds",
        "Dinafem",
        "Ethos Genetics",
        "Crop King Seeds",
        "ILGM",
        "Greenhouse Seed Co.",
        "DNA Genetics",
        "Exotic Genetix",
        "Compound Genetics",
        "In House Genetics",
        "Seed Junky Genetics",
        "Ace Seeds",
        "Paradise Seeds",
        "Serious Seeds",
        "Bodhi Seeds",
        "Archive Seed Bank",
        "Atlas Seed",
        "Kannabia",
        "00 Seeds",
        "Delicious Seeds",
        "T.H.Seeds",
        "Female Seeds",
        "Mr. Nice Seedbank",
    )

    private val gardenBrands = listOf(
        "Burpee",
        "Baker Creek Heirloom Seeds",
        "Johnny's Selected Seeds",
        "Ferry-Morse",
        "Botanical Interests",
        "Seed Savers Exchange",
        "Bonnie Plants",
    )

    private val varietiesByType = mapOf(
        PlantType.CANNABIS to listOf(
            "Northern Lights", "Blue Dream", "OG Kush", "Gorilla Glue #4", "Gelato", "Wedding Cake",
            "Girl Scout Cookies", "Sour Diesel", "White Widow", "Amnesia Haze", "Zkittlez", "Purple Punch",
            "Runtz", "Blueberry", "AK-47", "Jack Herer", "Granddaddy Purple", "Pineapple Express",
            "Bruce Banner", "Do-Si-Dos", "Mimosa", "Super Lemon Haze", "Skunk #1", "Critical Kush",
            "Cheese", "Tangie", "Durban Poison", "Trainwreck", "Chemdawg",
        ),
        PlantType.TOMATO to listOf(
            "Cherokee Purple", "Brandywine", "San Marzano", "Sungold", "Roma", "Early Girl", "Better Boy",
            "Big Beef", "Black Krim", "Green Zebra", "Mortgage Lifter", "Celebrity", "Sweet 100",
        ),
        PlantType.PEPPER to listOf(
            "Jalapeño", "California Wonder", "Habanero", "Serrano", "Cayenne", "Shishito", "Poblano",
            "Thai", "Ghost Pepper", "Carolina Reaper", "Scotch Bonnet", "Banana Pepper",
        ),
        PlantType.HERB to listOf(
            "Genovese Basil", "Thai Basil", "Cilantro", "Italian Parsley", "Curly Parsley", "Dill", "Mint",
            "Rosemary", "Thyme", "Oregano", "Chives", "Sage",
        ),
        PlantType.LEAFY_GREEN to listOf(
            "Buttercrunch Lettuce", "Romaine", "Black Seeded Simpson", "Lacinato Kale", "Swiss Chard",
            "Spinach", "Arugula", "Bok Choy",
        ),
        PlantType.MICROGREENS to listOf("Broccoli", "Radish", "Sunflower", "Pea Shoots", "Mustard", "Amaranth"),
    )

    /** Generic first, then brands that fit the plant type, then the rest. */
    fun manufacturers(type: PlantType): List<String> {
        val (first, second) = if (type == PlantType.CANNABIS) cannabisBreeders to gardenBrands else gardenBrands to cannabisBreeders
        return listOf(GENERIC) + first + second
    }

    /**
     * Strain suggestions for a manufacturer: a breeder's own lineup when we have it, nothing for
     * a cannabis breeder we couldn't verify (rather than other breeders' strains), and popular
     * varieties of the plant type for Generic, garden brands or anything typed in. Strains the
     * user already entered for this manufacturer ([userStrains]) are always included.
     */
    fun strains(type: PlantType, manufacturer: String, userStrains: List<String> = emptyList()): List<String> {
        val name = manufacturer.trim()
        val lineup = breederStrains.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val base = when {
            lineup != null -> lineup.filter { it.isNotBlank() }
            cannabisBreeders.any { it.equals(name, ignoreCase = true) } -> emptyList()
            else -> varietiesByType[type].orEmpty()
        }
        return (base + userStrains.map { it.trim() }.filter { it.isNotEmpty() })
            .distinctBy { it.lowercase() }
    }
}
