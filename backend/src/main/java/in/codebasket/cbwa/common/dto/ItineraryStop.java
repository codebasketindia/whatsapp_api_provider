package in.codebasket.cbwa.common.dto;

import jakarta.validation.constraints.Size;

/** One stop of a package's itinerary: the round / day it belongs to, the place, an optional time and note. */
public record ItineraryStop(@Size(max = 80) String round, @Size(max = 160) String place,
                            @Size(max = 40) String time, @Size(max = 500) String note) {
}
