package com.z.grow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test fun knownBreederShowsOnlyItsOwnStrains() {
        val strains = Catalog.strains(PlantType.CANNABIS, "Mephisto Genetics")
        assertTrue("Double Grape" in strains)
        assertFalse("Bruce Banner" in strains)
        assertFalse("Granddaddy Purple" in strains)
    }

    @Test fun breederMatchIgnoresCaseAndSpaces() {
        assertEquals(Catalog.strains(PlantType.CANNABIS, "Mephisto Genetics"), Catalog.strains(PlantType.CANNABIS, "  mephisto genetics "))
    }

    @Test fun userEnteredStrainsAreAddedForThatBreeder() {
        val strains = Catalog.strains(PlantType.CANNABIS, "Mephisto Genetics", listOf("Gorillaz Skittlez", "double grape"))
        assertTrue("Gorillaz Skittlez" in strains)
        assertEquals(1, strains.count { it.equals("Double Grape", ignoreCase = true) })
    }

    @Test fun genericOrUnlistedBreederFallsBackToPopularStrains() {
        assertTrue("Granddaddy Purple" in Catalog.strains(PlantType.CANNABIS, "Generic"))
        assertTrue("Granddaddy Purple" in Catalog.strains(PlantType.CANNABIS, "Night Owl Seeds"))
        assertTrue("Brandywine" in Catalog.strains(PlantType.TOMATO, "Burpee"))
    }
}
