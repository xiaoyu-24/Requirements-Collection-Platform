package com.xqfx.requirements.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstraptest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "ADMIN_INITIAL_PASSWORD=PublicBootstrapTest123!"
})
@ActiveProfiles("test")
class UserBootstrapTest {

    @Autowired
    private UserRepository repository;

    @Autowired
    private UserBootstrap bootstrap;

    @Autowired
    private AuthService authService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void startupCreatesOneAdministratorUsingConfiguredEnvironmentVariable() {
        var admin = repository.findByUsername("admin").orElseThrow();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.mustChangePassword()).isTrue();
        assertThat(encoder.matches("PublicBootstrapTest123!", admin.passwordHash())).isTrue();
    }

    @Test
    void restartDoesNotOverwriteExistingAdministrator() {
        var originalHash = repository.findByUsername("admin").orElseThrow().passwordHash();
        bootstrap.initialize(repository, authService, "DifferentStartupPassword123!");
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByUsername("admin").orElseThrow().passwordHash()).isEqualTo(originalHash);
    }

    @Test
    void missingPasswordGeneratesDistinctRandomPasswordsAndStoresOnlyHashes() {
        var emptyRepository = mock(UserRepository.class);
        var hashingService = mock(AuthService.class);
        when(hashingService.encodePassword(anyString())).thenAnswer(invocation ->
                encoder.encode(invocation.getArgument(0, String.class)));

        bootstrap.initialize(emptyRepository, hashingService, "");
        var firstUser = ArgumentCaptor.forClass(UserEntity.class);
        verify(emptyRepository).save(firstUser.capture());
        var firstPassword = ArgumentCaptor.forClass(String.class);
        verify(hashingService).encodePassword(firstPassword.capture());
        assertThat(firstPassword.getValue()).hasSize(12);
        assertThat(encoder.matches(firstPassword.getValue(), firstUser.getValue().passwordHash())).isTrue();
        assertThat(firstUser.getValue().mustChangePassword()).isTrue();

        var anotherRepository = mock(UserRepository.class);
        var anotherService = mock(AuthService.class);
        bootstrap.initialize(anotherRepository, anotherService, " ");
        var nextPassword = ArgumentCaptor.forClass(String.class);
        verify(anotherService).encodePassword(nextPassword.capture());
        assertThat(nextPassword.getValue()).hasSize(12).isNotEqualTo(firstPassword.getValue());
    }
}
