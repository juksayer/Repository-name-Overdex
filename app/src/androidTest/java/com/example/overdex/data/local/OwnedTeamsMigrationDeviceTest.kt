package com.example.overdex.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnedTeamsMigrationDeviceTest {
    @Test fun versionNineCardsSurviveMigrationAndPersistMultipleTeams() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "teams-migration-test-${System.nanoTime()}"
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        var database: PokedexDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                old.execSQL("""CREATE TABLE pokemon (
                    id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, typesJson TEXT NOT NULL,
                    region TEXT NOT NULL, genus TEXT NOT NULL, height TEXT NOT NULL, weight TEXT NOT NULL,
                    prevEvolutionsJson TEXT NOT NULL, nextEvolutionsJson TEXT NOT NULL,
                    fastMovesJson TEXT NOT NULL, chargedMovesJson TEXT NOT NULL, spriteUrl TEXT NOT NULL,
                    cryUrl TEXT NOT NULL, description TEXT NOT NULL,
                    baseAttack INTEGER NOT NULL, baseDefense INTEGER NOT NULL, baseStamina INTEGER NOT NULL
                )""")
                old.execSQL("""CREATE TABLE owned_pokemon (
                    id TEXT NOT NULL PRIMARY KEY, speciesId INTEGER NOT NULL, displayName TEXT, cp INTEGER,
                    isShadow INTEGER NOT NULL, isPurified INTEGER NOT NULL, isShiny INTEGER NOT NULL,
                    isFavorite INTEGER NOT NULL, fastMove TEXT, chargedMove1 TEXT, chargedMove2 TEXT,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                )""")
                old.execSQL("INSERT INTO pokemon VALUES (6, 'Charizard', '[]', 'Kanto', '', '', '', '[]', '[]', '[]', '[]', '', '', '', 223, 173, 186)")
                old.execSQL("INSERT INTO owned_pokemon VALUES ('my-charizard', 6, 'Ember:FIRE:', 1499, 1, 0, 0, 1, 'Wing Attack', 'Blast Burn', 'Dragon Claw', 100, 200)")
                old.version = 9
            }

            fun open() = Room.databaseBuilder(context, PokedexDatabase::class.java, name)
                .addMigrations(PokedexDatabase.MIGRATION_9_10, PokedexDatabase.MIGRATION_10_11,
                    PokedexDatabase.MIGRATION_11_12).build()
            val migrated = open().also { database = it }
            val original = requireNotNull(migrated.ownedPokemonDao().getOwnedPokemonById("my-charizard").first()).toDomain()
            assertEquals("Ember:FIRE:", original.displayName)
            assertEquals(1499, original.cp)
            assertEquals(true, original.isShadow)
            assertEquals(true, original.isFavorite)
            assertEquals("Dragon Claw", original.chargedMove2)
            assertEquals(emptyList<String>(), original.teams)
            assertEquals(com.example.overdex.model.PokemonSize.UNKNOWN, original.size)
            assertNull(original.speciesName)
            assertEquals("my-charizard", original.id)
            assertEquals(100L, original.createdAt)
            assertEquals(200L, original.updatedAt)
            assertEquals(1, migrated.pokemonDao().getCount())
            val updated = original.copy(teams = listOf("Great League", "Coach practice"),
                size = com.example.overdex.model.PokemonSize.XL, speciesName = "Charizard")
            migrated.ownedPokemonDao().updateOwnedPokemon(updated.toEntity())
            migrated.close()

            val reopened = open().also { database = it }
            assertEquals(updated, reopened.ownedPokemonDao().getOwnedPokemonById("my-charizard").first()?.toDomain())
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }
}
