package com.clinica.recordatorios;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import com.clinica.recordatorios.service.CitaNoEncontradaException;
import com.clinica.recordatorios.service.CitaReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class CitaReminderServiceIntegrationTest {

    @TestConfiguration
    static class ClockDeterministaConfig {
        // Nombre distinto de "clock" para no colisionar con ClockConfig
        // (el override de beans está deshabilitado por defecto).
        @Bean
        @Primary
        public Clock clockDeterminista() {
            return Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @Autowired
    private CitaReminderService citaReminderService;

    @Autowired
    private CitaRepository citaRepository;

    @MockBean
    private NotificadorSmsGateway notificadorSmsGateway;

    @BeforeEach
    void limpiarBaseDeDatos() {
        citaRepository.deleteAll();
    }

    @Test
    void enviarRecordatoriosPendientes_contextoCompletoDeSpring_persisteYNotificaCitasEnVentana() {
        citaRepository.save(new Cita(null, "Carla Ríos", "3011112222",
                LocalDateTime.of(2026, 8, 10, 18, 0), CanalNotificacion.SMS, false));

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(1, enviados);
        verify(notificadorSmsGateway, times(1)).enviarSms(eq("3011112222"), anyString());
        assertTrue(citaRepository.findAll().get(0).isRecordatorioEnviado());
    }

    @Test
    void enviarRecordatoriosPendientes_citaFueraDeVentana_noNotificaNiPersisteCambios() {
        citaRepository.save(new Cita(null, "Pedro Gil", "3022223333",
                LocalDateTime.of(2026, 8, 12, 18, 0), CanalNotificacion.SMS, false));

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(0, enviados);
        verifyNoInteractions(notificadorSmsGateway);
        assertFalse(citaRepository.findAll().get(0).isRecordatorioEnviado());
    }

    @Test
    void enviarRecordatorioUrgente_contextoCompletoDeSpring_usaElCanalUrgenteConElGatewayMockeado() {
        Cita guardada = citaRepository.save(new Cita(null, "Sara León", "3033334444",
                LocalDateTime.of(2026, 8, 10, 10, 30), CanalNotificacion.EMAIL, false));

        citaReminderService.enviarRecordatorioUrgente(guardada.getId());

        verify(notificadorSmsGateway).enviarSms(eq("3033334444"), contains("URGENTE"));
        assertTrue(citaRepository.findById(guardada.getId()).orElseThrow().isRecordatorioEnviado());
    }

    @Test
    void enviarRecordatorioUrgente_gatewayFalla_noPersisteElEnvio() {
        Cita guardada = citaRepository.save(new Cita(null, "Tomás Vega", "3044445555",
                LocalDateTime.of(2026, 8, 10, 10, 30), CanalNotificacion.SMS, false));
        doThrow(new IllegalStateException("Proveedor de SMS caído"))
                .when(notificadorSmsGateway).enviarSms(anyString(), anyString());

        assertThrows(IllegalStateException.class,
                () -> citaReminderService.enviarRecordatorioUrgente(guardada.getId()));

        assertFalse(citaRepository.findById(guardada.getId()).orElseThrow().isRecordatorioEnviado());
    }

    @Test
    void enviarRecordatorioUrgente_citaInexistente_lanzaCitaNoEncontradaException() {
        assertThrows(CitaNoEncontradaException.class,
                () -> citaReminderService.enviarRecordatorioUrgente(9999L));
        verifyNoInteractions(notificadorSmsGateway);
    }
}
