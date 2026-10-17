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

    @Test fun genericGardenOrCustomBrandsGetPopularVarieties() {
        assertTrue("Granddaddy Purple" in Catalog.strains(PlantType.CANNABIS, "Generic"))
        assertTrue("Granddaddy Purple" in Catalog.strains(PlantType.CANNABIS, "My Buddy's Bagseed"))
        assertTrue("Brandywine" in Catalog.strains(PlantType.TOMATO, "Burpee"))
    }

    @Test fun verifiedLineupsContainKnownStrains() {
        assertTrue("Gorilla Zkittlez Auto" in Catalog.strains(PlantType.CANNABIS, "FastBuds"))
        assertTrue("Blackberry Auto" in Catalog.strains(PlantType.CANNABIS, "FastBuds"))
        assertTrue("Tangie Auto" in Catalog.strains(PlantType.CANNABIS, "FastBuds"))
        assertTrue("CPT OG Auto" in Catalog.strains(PlantType.CANNABIS, "Night Owl Seeds"))
        assertFalse("Forum Cookies" in Catalog.strains(PlantType.CANNABIS, "Mephisto Genetics"))
        assertFalse("Granddaddy Purple" in Catalog.strains(PlantType.CANNABIS, "Night Owl Seeds"))
    }

    @Test fun everyListedLineupHasCleanNames() {
        breederStrains.forEach { (breeder, strains) ->
            val names = strains.filter { it.isNotBlank() }
            assertTrue("$breeder is empty", names.isNotEmpty())
            names.forEach { assertEquals("$breeder: '$it'", it.trim(), it) }
            assertEquals("$breeder has duplicates", names.size, names.distinctBy { it.lowercase() }.size)
        }
    }

    @Test fun unverifiedBreederShowsOnlyTheUsersOwnStrains() {
        assertTrue(Catalog.strains(PlantType.CANNABIS, "Humboldt Seed Company").isEmpty())
        assertEquals(listOf("Blueberry Muffin"), Catalog.strains(PlantType.CANNABIS, "Humboldt Seed Company", listOf("Blueberry Muffin")))
    }
}
