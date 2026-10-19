package in.codebasket.cbwa.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "opt_outs")
public class OptOut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String phone;

    private Long phoneNumberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OptOutSource source;

    private String keyword;

    @Column(nullable = false)
    private Instant createdAt;

    private Long createdBy;
}
