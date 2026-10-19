package in.codebasket.cbwa.common.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "number_sequences")
public class NumberSequence {

    @Id
    private String name;

    @Column(nullable = false)
    private String prefix;

    @Column(nullable = false)
    private long nextValue;

    @Column(nullable = false)
    private Instant updatedAt;
}
