package com.sweethomecompany.crm.security;

import com.sweethomecompany.crm.user.AppUser;
import com.sweethomecompany.crm.user.AppUserRepository;
import com.sweethomecompany.crm.user.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminUserSeederTest {

    private static final UUID DEFAULT_AGENCY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void createsOneAdminWhenTheTableIsEmpty() {
        AppUserRepository repository = mock(AppUserRepository.class);
        when(repository.count()).thenReturn(0L);
        AdminUserSeeder seeder = new AdminUserSeeder(
            repository, new BCryptPasswordEncoder(), "admin@example.com", "change-me-now", DEFAULT_AGENCY_ID);

        seeder.run(null);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isActive()).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("change-me-now", saved.getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenAUserAlreadyExists() {
        AppUserRepository repository = mock(AppUserRepository.class);
        when(repository.count()).thenReturn(1L);
        AdminUserSeeder seeder = new AdminUserSeeder(
            repository, new BCryptPasswordEncoder(), "admin@example.com", "change-me-now", DEFAULT_AGENCY_ID);

        seeder.run(null);

        verify(repository, never()).save(any());
    }
}
