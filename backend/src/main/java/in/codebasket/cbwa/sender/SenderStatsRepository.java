package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.messaging.Message;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Read-only message statistics per sender (sender module view on the messages table). */
public interface SenderStatsRepository extends Repository<Message, Long> {

    @Query("select count(m) from Message m where m.phoneNumberId = :senderId "
            + "and m.direction = in.codebasket.cbwa.messaging.MessageDirection.OUTBOUND and m.createdAt >= :since")
    long countOutboundSince(@Param("senderId") Long senderId, @Param("since") Instant since);

    /** Rows: [ist_date, sent, delivered, read, failed, charged] for outbound messages of a sender. */
    @Query(value = """
            select date_format(convert_tz(m.created_at, '+00:00', '+05:30'), '%Y-%m-%d') as d,
                   sum(case when m.status in ('SENT','DELIVERED','READ') then 1 else 0 end),
                   sum(case when m.status in ('DELIVERED','READ') then 1 else 0 end),
                   sum(case when m.status = 'READ' then 1 else 0 end),
                   sum(case when m.status = 'FAILED' then 1 else 0 end),
                   coalesce(sum(case when m.charge_status = 'CHARGED' then m.price else 0 end), 0)
            from messages m
            where m.phone_number_id = :senderId and m.direction = 'OUTBOUND' and m.created_at >= :from and m.created_at < :to
            group by d order by d
            """, nativeQuery = true)
    List<Object[]> dailyStats(@Param("senderId") Long senderId, @Param("from") Instant from, @Param("to") Instant to);

    /** Rows: [ist_date, sent, delivered, read] for a template. */
    @Query(value = """
            select date_format(convert_tz(m.created_at, '+00:00', '+05:30'), '%Y-%m-%d') as d,
                   sum(case when m.status in ('SENT','DELIVERED','READ') then 1 else 0 end),
                   sum(case when m.status in ('DELIVERED','READ') then 1 else 0 end),
                   sum(case when m.status = 'READ' then 1 else 0 end)
            from messages m
            where m.template_id = :templateId and m.direction = 'OUTBOUND' and m.created_at >= :from and m.created_at < :to
            group by d order by d
            """, nativeQuery = true)
    List<Object[]> templateDailyStats(@Param("templateId") Long templateId, @Param("from") Instant from, @Param("to") Instant to);
}
