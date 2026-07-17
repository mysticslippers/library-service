package me.ifmo.backend.services.impl;

import me.ifmo.backend.catalog.web.response.MaterialShortResponse;
import me.ifmo.backend.dto.circulation.request.CancelReservationRequest;
import me.ifmo.backend.dto.circulation.request.CreateReservationRequest;
import me.ifmo.backend.dto.circulation.request.ReservationSearchRequest;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.library.domain.LibraryRule;
import me.ifmo.backend.catalog.domain.Material;
import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.entities.Reservation;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.library.domain.enums.BranchStatus;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;
import me.ifmo.backend.entities.enums.ReservationStatus;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.ResourceInUseException;
import me.ifmo.backend.catalog.mapper.MaterialMapper;
import me.ifmo.backend.mappers.ReservationMapper;
import me.ifmo.backend.library.persistence.BranchRepository;
import me.ifmo.backend.repositories.FineRepository;
import me.ifmo.backend.library.persistence.LibraryRuleRepository;
import me.ifmo.backend.repositories.LoanRepository;
import me.ifmo.backend.catalog.persistence.MaterialAuthorRepository;
import me.ifmo.backend.catalog.persistence.MaterialCopyRepository;
import me.ifmo.backend.catalog.persistence.MaterialGenreRepository;
import me.ifmo.backend.catalog.persistence.MaterialRepository;
import me.ifmo.backend.repositories.ReservationRepository;
import me.ifmo.backend.user.persistence.UserBlockRepository;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long STAFF_ID = 3L;
    private static final long MATERIAL_ID = 4L;
    private static final long COPY_ID = 5L;
    private static final long BRANCH_ID = 6L;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private MaterialCopyRepository materialCopyRepository;
    @Mock
    private LibraryRuleRepository libraryRuleRepository;
    @Mock
    private MaterialAuthorRepository materialAuthorRepository;
    @Mock
    private MaterialGenreRepository materialGenreRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private FineRepository fineRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private MaterialMapper materialMapper;

    @InjectMocks
    private ReservationServiceImpl service;

    private void grantStaff() {
        when(userRoleRepository.findRoleCodesByUser_Id(ReservationServiceImplTest.STAFF_ID)).thenReturn(List.of(RoleCode.LIBRARIAN));
    }

    private void allowReservations(User user) {
        when(userBlockRepository.existsByUser_IdAndStatus(user.getId(), UserBlockStatus.ACTIVE)).thenReturn(false);
        when(fineRepository.countByUser_IdAndStatus(user.getId(), FineStatus.ACTIVE)).thenReturn(0L);
        when(loanRepository.countByUser_IdAndStatusIn(eq(user.getId()), anyCollection())).thenReturn(0L);
    }

    private void stubResponse(Reservation reservation, ReservationResponse response) {
        MaterialShortResponse shortResponse = org.mockito.Mockito.mock(MaterialShortResponse.class);
        when(materialAuthorRepository.findByMaterial_IdOrderByAuthorOrderAsc(reservation.getMaterial().getId()))
                .thenReturn(List.of());
        when(materialGenreRepository.findByMaterial_Id(reservation.getMaterial().getId())).thenReturn(List.of());
        when(materialMapper.toShortResponse(eq(reservation.getMaterial()), anyList(), anyList()))
                .thenReturn(shortResponse);
        when(reservationMapper.toResponse(reservation, shortResponse)).thenReturn(response);
    }

    private User activeUser() {
        return User.builder().id(ReservationServiceImplTest.USER_ID).status(UserStatus.ACTIVE).build();
    }

    private Material activeMaterial() {
        return Material.builder().id(MATERIAL_ID).status(MaterialStatus.ACTIVE).build();
    }

    private Branch activeBranch() {
        return Branch.builder().id(BRANCH_ID).status(BranchStatus.ACTIVE).build();
    }

    private MaterialCopy availableCopy(Material material, Branch branch) {
        return MaterialCopy.builder()
                .id(COPY_ID)
                .material(material)
                .branch(branch)
                .status(CopyStatus.AVAILABLE)
                .build();
    }

    private LibraryRule libraryRule() {
        return LibraryRule.builder()
                .branch(activeBranch())
                .maxActiveReservations(5)
                .reservationTtlDays(3)
                .reservationAllowed(true)
                .status(LibraryRuleStatus.ACTIVE)
                .build();
    }

    @Test
    void createReservesAvailableCopyAndCreatesActiveReservation() {
        User user = activeUser();
        Material material = activeMaterial();
        Branch branch = activeBranch();
        MaterialCopy copy = availableCopy(material, branch);
        LibraryRule rule = libraryRule();
        Reservation reservation = Reservation.builder()
                .user(user)
                .material(material)
                .copy(copy)
                .branch(branch)
                .build();
        ReservationResponse response = org.mockito.Mockito.mock(ReservationResponse.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        allowReservations(user);
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(libraryRuleRepository.findActualByBranchIdAndStatus(
                eq(BRANCH_ID), eq(LibraryRuleStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(Optional.of(rule));
        when(reservationRepository.countByUser_IdAndStatusIn(eq(USER_ID), anyCollection())).thenReturn(0L);
        when(reservationRepository.existsByUser_IdAndMaterial_IdAndStatusIn(
                eq(USER_ID), eq(MATERIAL_ID), anyCollection())).thenReturn(false);
        when(materialCopyRepository.findById(COPY_ID)).thenReturn(Optional.of(copy));
        when(reservationMapper.toEntity(
                eq(user), eq(material), eq(copy), eq(branch), any(LocalDateTime.class)))
                .thenReturn(reservation);
        when(reservationRepository.save(reservation)).thenReturn(reservation);
        stubResponse(reservation, response);

        LocalDateTime before = LocalDateTime.now();
        ReservationResponse result = service.create(
                USER_ID,
                new CreateReservationRequest(USER_ID, MATERIAL_ID, COPY_ID, BRANCH_ID)
        );

        assertThat(result).isSameAs(response);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.RESERVED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        ArgumentCaptor<LocalDateTime> expiration = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reservationMapper).toEntity(eq(user), eq(material), eq(copy), eq(branch), expiration.capture());
        assertThat(expiration.getValue()).isAfterOrEqualTo(before.plusDays(3));
    }

    @Test
    void createRejectsActorWhoIsNeitherOwnerNorStaff() {
        when(userRoleRepository.findRoleCodesByUser_Id(OTHER_USER_ID)).thenReturn(List.of(RoleCode.READER));

        assertThatThrownBy(() -> service.create(
                OTHER_USER_ID,
                new CreateReservationRequest(USER_ID, MATERIAL_ID, COPY_ID, BRANCH_ID)
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access is denied");

        verify(userRepository, never()).findById(anyLong());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createRejectsUserWithUnpaidFine() {
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userBlockRepository.existsByUser_IdAndStatus(USER_ID, UserBlockStatus.ACTIVE)).thenReturn(false);
        when(fineRepository.countByUser_IdAndStatus(USER_ID, FineStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(
                USER_ID,
                new CreateReservationRequest(USER_ID, MATERIAL_ID, COPY_ID, BRANCH_ID)
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("User has unpaid fines");

        verify(materialRepository, never()).findById(anyLong());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createRejectsUnavailableExplicitCopy() {
        User user = activeUser();
        Material material = activeMaterial();
        Branch branch = activeBranch();
        MaterialCopy copy = availableCopy(material, branch);
        copy.setStatus(CopyStatus.LOANED);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        allowReservations(user);
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(libraryRuleRepository.findActualByBranchIdAndStatus(
                eq(BRANCH_ID), eq(LibraryRuleStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(Optional.of(libraryRule()));
        when(reservationRepository.countByUser_IdAndStatusIn(eq(USER_ID), anyCollection())).thenReturn(0L);
        when(reservationRepository.existsByUser_IdAndMaterial_IdAndStatusIn(
                eq(USER_ID), eq(MATERIAL_ID), anyCollection())).thenReturn(false);
        when(materialCopyRepository.findById(COPY_ID)).thenReturn(Optional.of(copy));

        assertThatThrownBy(() -> service.create(
                USER_ID,
                new CreateReservationRequest(USER_ID, MATERIAL_ID, COPY_ID, BRANCH_ID)
        ))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessage("Material copy is not available for reservation");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelByUserTrimsReasonAndReleasesReservedCopy() {
        User user = activeUser();
        Material material = activeMaterial();
        MaterialCopy copy = availableCopy(material, activeBranch());
        copy.setStatus(CopyStatus.RESERVED);
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(user)
                .material(material)
                .copy(copy)
                .status(ReservationStatus.ACTIVE)
                .build();
        ReservationResponse response = org.mockito.Mockito.mock(ReservationResponse.class);

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(reservation)).thenReturn(reservation);
        stubResponse(reservation, response);

        LocalDateTime before = LocalDateTime.now();
        ReservationResponse result = service.cancelByUser(
                USER_ID, 10L, new CancelReservationRequest("  plans changed  ")
        );

        assertThat(result).isSameAs(response);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED_BY_USER);
        assertThat(reservation.getCancellationReason()).isEqualTo("plans changed");
        assertThat(reservation.getCancelledAt()).isAfterOrEqualTo(before);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void cancelByUserRejectsDifferentOwner() {
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(activeUser())
                .status(ReservationStatus.ACTIVE)
                .build();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() ->
                service.cancelByUser(OTHER_USER_ID, 10L, new CancelReservationRequest("reason")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access is denied");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void expireRejectsReservationWhoseExpirationTimeHasNotPassed() {
        Reservation reservation = Reservation.builder()
                .id(10L)
                .status(ReservationStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        grantStaff();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.expire(STAFF_ID, 10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Reservation has not expired yet");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void markReadyForPickupSetsStatusAndTimestamp() {
        User user = activeUser();
        Material material = activeMaterial();
        MaterialCopy copy = availableCopy(material, activeBranch());
        copy.setStatus(CopyStatus.RESERVED);
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(user)
                .material(material)
                .copy(copy)
                .status(ReservationStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        ReservationResponse response = org.mockito.Mockito.mock(ReservationResponse.class);

        grantStaff();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(reservation)).thenReturn(reservation);
        stubResponse(reservation, response);

        LocalDateTime before = LocalDateTime.now();
        ReservationResponse result = service.markReadyForPickup(STAFF_ID, 10L);

        assertThat(result).isSameAs(response);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.READY_FOR_PICKUP);
        assertThat(reservation.getReadyAt()).isAfterOrEqualTo(before);
    }

    @Test
    void getReservationByIdAllowsOwner() {
        User user = activeUser();
        Material material = activeMaterial();
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(user)
                .material(material)
                .status(ReservationStatus.ACTIVE)
                .build();
        ReservationResponse response = org.mockito.Mockito.mock(ReservationResponse.class);
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        stubResponse(reservation, response);

        assertThat(service.getReservationById(USER_ID, 10L)).isSameAs(response);
        verify(userRoleRepository, never()).findRoleCodesByUser_Id(anyLong());
    }

    @Test
    void cancelByLibrarianCancelsAndReleasesCopy() {
        Material material = activeMaterial();
        MaterialCopy copy = availableCopy(material, activeBranch());
        copy.setStatus(CopyStatus.RESERVED);
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(activeUser())
                .material(material)
                .copy(copy)
                .status(ReservationStatus.READY_FOR_PICKUP)
                .build();
        ReservationResponse response = org.mockito.Mockito.mock(ReservationResponse.class);

        grantStaff();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(reservation)).thenReturn(reservation);
        stubResponse(reservation, response);

        ReservationResponse result = service.cancelByLibrarian(
                STAFF_ID, 10L, new CancelReservationRequest("library closed")
        );

        assertThat(result).isSameAs(response);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED_BY_LIBRARIAN);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void expireMarksPastReservationExpiredAndReleasesCopy() {
        Material material = activeMaterial();
        MaterialCopy copy = availableCopy(material, activeBranch());
        copy.setStatus(CopyStatus.RESERVED);
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(activeUser())
                .material(material)
                .copy(copy)
                .status(ReservationStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        ReservationResponse response = org.mockito.Mockito.mock(ReservationResponse.class);

        grantStaff();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(reservation)).thenReturn(reservation);
        stubResponse(reservation, response);

        assertThat(service.expire(STAFF_ID, 10L)).isSameAs(response);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void markUsedConvertsReadyReservation() {
        Material material = activeMaterial();
        MaterialCopy copy = availableCopy(material, activeBranch());
        copy.setStatus(CopyStatus.RESERVED);
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(activeUser())
                .material(material)
                .copy(copy)
                .status(ReservationStatus.READY_FOR_PICKUP)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        ReservationResponse response = org.mockito.Mockito.mock(ReservationResponse.class);

        grantStaff();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(reservation)).thenReturn(reservation);
        stubResponse(reservation, response);

        assertThat(service.markUsed(STAFF_ID, 10L)).isSameAs(response);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.USED);
    }

    @Test
    void searchRestrictsReaderToOwnReservationsAndMapsPage() {
        User user = activeUser();
        Material material = activeMaterial();
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(user)
                .material(material)
                .status(ReservationStatus.ACTIVE)
                .build();
        ReservationResponse response = org.mockito.Mockito.mock(ReservationResponse.class);
        PageRequest pageable = PageRequest.of(0, 20);
        LocalDateTime now = LocalDateTime.now();
        ReservationSearchRequest request = new ReservationSearchRequest(
                USER_ID, MATERIAL_ID, COPY_ID, BRANCH_ID, ReservationStatus.ACTIVE,
                now.minusDays(1), now, now.plusDays(1), "book"
        );

        when(userRoleRepository.findRoleCodesByUser_Id(USER_ID)).thenReturn(List.of(RoleCode.READER));
        when(reservationRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Reservation>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(reservation), pageable, 1));
        stubResponse(reservation, response);

        PageResponse<ReservationResponse> result = service.search(USER_ID, request, pageable);

        assertThat(result.content()).containsExactly(response);
        assertThat(result.totalElements()).isEqualTo(1);
    }
}
