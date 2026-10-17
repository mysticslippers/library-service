package me.ifmo.backend.circulation.web;

import me.ifmo.backend.circulation.application.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationControllerTest {

    @Test
    void cannotMarkReservationUsedWithoutCreatingLoan() throws Exception {
        ReservationService service = mock(ReservationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ReservationController(service)).build();

        mvc.perform(post("/reservations/1/mark-used"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }
}
