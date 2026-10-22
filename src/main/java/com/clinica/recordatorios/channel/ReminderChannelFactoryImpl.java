package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.CanalNotificacion;
import org.springframework.stereotype.Component;

@Component
public class ReminderChannelFactoryImpl implements ReminderChannelFactory {

    private final EmailReminderChannel emailReminderChannel;
    private final SmsReminderChannel smsReminderChannel;

    public ReminderChannelFactoryImpl(EmailReminderChannel emailReminderChannel,
                                      SmsReminderChannel smsReminderChannel) {
        this.emailReminderChannel = emailReminderChannel;
        this.smsReminderChannel = smsReminderChannel;
    }

    @Override
    public ReminderChannel obtenerCanal(CanalNotificacion tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("El canal de notificación es obligatorio");
        }
        return switch (tipo) {
            case EMAIL -> emailReminderChannel;
            case SMS -> smsReminderChannel;
        };
    }
}
