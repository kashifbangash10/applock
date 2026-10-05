package com.applockerapp.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-level Dagger Hilt module.
 * InstallIn(SingletonComponent) means these dependencies live for
 * the entire app lifecycle - created once, destroyed with app.
 *
 * Add providers here for:
 * - Application context
 * - SharedPreferences
 * - Database (Room)
 * - Retrofit instance
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Singleton dependencies will be added here as the app grows
    // Example:
    // @Provides
    // @Singleton
    // fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
    //     return Room.databaseBuilder(context, AppDatabase::class.java, "applocker_db").build()
    // }
}
