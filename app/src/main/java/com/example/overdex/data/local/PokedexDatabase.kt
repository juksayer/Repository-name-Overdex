package com.example.overdex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PokemonEntity::class], version = 3, exportSchema = false)
abstract class PokedexDatabase : RoomDatabase() {
    abstract fun pokemonDao(): PokemonDao

    companion object {
        @Volatile
        private var INSTANCE: PokedexDatabase? = null

        fun getDatabase(context: Context): PokedexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PokedexDatabase::class.java,
                    "pokedex_database",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
