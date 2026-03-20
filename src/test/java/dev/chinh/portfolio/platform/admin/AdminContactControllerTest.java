package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.platform.admin.dto.AdminContactDetailDto;
import dev.chinh.portfolio.platform.admin.dto.AdminContactListDto;
import dev.chinh.portfolio.platform.contact.ContactSubmission;
import dev.chinh.portfolio.platform.contact.ContactSubmissionRepository;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdminContactController}.
 *
 * <p>Authorization: OWNER role required for all endpoints. Uses @PreAuthorize
 * + ensureOwner() guard.
 *
 * <p>Note: @PreAuthorize("hasRole('OWNER')") is evaluated by Spring Security
 * after the controller method is invoked — so we inject an authenticated principal
 * with ROLE_OWNER authority for happy-path tests.
 */
@ExtendWith(MockitoExtension.class)
class AdminContactControllerTest {

    private static final UUID SUB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AdminContactService service;

    @InjectMocks
    private AdminContactController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private AdminContactDetailDto makeDto(UUID id) {
        return new AdminContactDetailDto(
                id,
                "recruiter@example.com",
                "I'd like to discuss the backend role.",
                "linkedin",
                Instant.parse("2026-03-15T10:00:00Z"),
                false
        );
    }

    private void setOwnerAuth() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "owner-uuid",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private void setNonOwnerAuth() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "user-uuid",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // ── listSubmissions ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/admin/contacts")
    class ListSubmissionsTests {

        @Test
        @DisplayName("should return 200 with paginated list when caller is Owner")
        void list_asOwner_returnsPaginatedList() {
            setOwnerAuth();
            AdminContactListDto list = new AdminContactListDto(
                    List.of(makeDto(SUB_ID), makeDto(OTHER_ID)),
                    0, 20, 2L, 1
            );
            when(service.listSubmissions(0, 20)).thenReturn(list);

            var response = controller.listSubmissions(0, 20);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().content()).hasSize(2);
            assertThat(response.getBody().totalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should enforce max page size of 100")
        void list_pageSizeExceedsMax_isCappedTo100() {
            setOwnerAuth();
            AdminContactListDto list = new AdminContactListDto(List.of(), 0, 100, 0L, 0);
            when(service.listSubmissions(0, 100)).thenReturn(list);

            controller.listSubmissions(0, 500);

            verify(service).listSubmissions(0, 100);
        }

        @Test
        @DisplayName("should throw ForbiddenException when caller is not Owner")
        void list_asNonOwner_throwsForbiddenException() {
            setNonOwnerAuth();

            assertThatThrownBy(() -> controller.listSubmissions(0, 20))
                    .isInstanceOf(dev.chinh.portfolio.shared.error.ForbiddenException.class);
        }
    }

    // ── getSubmission ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/admin/contacts/{id}")
    class GetSubmissionTests {

        @Test
        @DisplayName("should return 200 with submission detail when found")
        void get_asOwner_found() {
            setOwnerAuth();
            AdminContactDetailDto dto = makeDto(SUB_ID);
            when(service.getSubmission(SUB_ID)).thenReturn(dto);

            var response = controller.getSubmission(SUB_ID);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().email()).isEqualTo("recruiter@example.com");
            assertThat(response.getBody().message()).isEqualTo("I'd like to discuss the backend role.");
            assertThat(response.getBody().referralSource()).isEqualTo("linkedin");
        }

        @Test
        @DisplayName("should propagate EntityNotFoundException when not found")
        void get_notFound_throwsEntityNotFoundException() {
            setOwnerAuth();
            when(service.getSubmission(OTHER_ID))
                    .thenThrow(new EntityNotFoundException("Contact submission not found: " + OTHER_ID));

            assertThatThrownBy(() -> controller.getSubmission(OTHER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ForbiddenException when caller is not Owner")
        void get_asNonOwner_throwsForbiddenException() {
            setNonOwnerAuth();

            assertThatThrownBy(() -> controller.getSubmission(SUB_ID))
                    .isInstanceOf(dev.chinh.portfolio.shared.error.ForbiddenException.class);
        }
    }

    // ── markAsRead ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/admin/contacts/{id}/read")
    class MarkAsReadTests {

        @Test
        @DisplayName("should return 200 with updated detail when submission exists")
        void markAsRead_asOwner_found() {
            setOwnerAuth();
            AdminContactDetailDto dto = new AdminContactDetailDto(
                    SUB_ID, "recruiter@example.com",
                    "Message", "linkedin",
                    Instant.now(), true
            );
            when(service.markAsRead(SUB_ID)).thenReturn(dto);

            var response = controller.markAsRead(SUB_ID);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().isRead()).isTrue();
        }

        @Test
        @DisplayName("should propagate EntityNotFoundException when not found")
        void markAsRead_notFound_throwsEntityNotFoundException() {
            setOwnerAuth();
            when(service.markAsRead(OTHER_ID))
                    .thenThrow(new EntityNotFoundException("Contact submission not found: " + OTHER_ID));

            assertThatThrownBy(() -> controller.markAsRead(OTHER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ForbiddenException when caller is not Owner")
        void markAsRead_asNonOwner_throwsForbiddenException() {
            setNonOwnerAuth();

            assertThatThrownBy(() -> controller.markAsRead(SUB_ID))
                    .isInstanceOf(dev.chinh.portfolio.shared.error.ForbiddenException.class);
        }
    }
}
