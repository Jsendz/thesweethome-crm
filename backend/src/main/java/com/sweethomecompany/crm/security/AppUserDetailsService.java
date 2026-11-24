package com.sweethomecompany.crm.security;

import com.sweethomecompany.crm.user.AppUserRepository;
import com.sweethomecompany.crm.user.EmailNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = EmailNormalizer.normalize(email);
        return appUserRepository.findByEmail(normalized)
            .map(AppUserPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("No user with email " + normalized));
    }
}
