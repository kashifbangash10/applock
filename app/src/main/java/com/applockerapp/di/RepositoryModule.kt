package com.applockerapp.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository bindings module.
 * This is where we bind Repository Interfaces to their Implementations.
 * This allows the domain layer to depend on abstractions, not concretions.
 *
 * Example pattern:
 * @Binds
 * @Singleton
 * abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    // Repository bindings will be added here as features are added
}
