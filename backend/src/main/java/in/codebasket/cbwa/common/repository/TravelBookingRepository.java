package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.constants.BookingStatus;
import in.codebasket.cbwa.common.entity.TravelBooking;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelBookingRepository extends JpaRepository<TravelBooking, Long>, JpaSpecificationExecutor<TravelBooking> {
    Optional<TravelBooking> findByUidAndPartnerId(String uid, Long partnerId);

    List<TravelBooking> findTop10ByPartnerIdAndPhoneOrderByIdDesc(Long partnerId, String phone);

    boolean existsByPackageId(Long packageId);

    /** Seats taken per package (confirmed and completed bookings). */
    @Query("select b.packageId, coalesce(sum(b.travellers), 0) from TravelBooking b where b.packageId in :ids"
            + " and b.status in (in.codebasket.cbwa.common.constants.BookingStatus.CONFIRMED, in.codebasket.cbwa.common.constants.BookingStatus.COMPLETED)"
            + " group by b.packageId")
    List<Object[]> seatsBooked(@Param("ids") Collection<Long> ids);

    @Query("select coalesce(sum(b.travellers), 0) from TravelBooking b where b.packageId = :id and b.id <> :excludeId"
            + " and b.status in (in.codebasket.cbwa.common.constants.BookingStatus.CONFIRMED, in.codebasket.cbwa.common.constants.BookingStatus.COMPLETED)")
    long seatsBookedExcluding(@Param("id") Long packageId, @Param("excludeId") Long excludeId);
}
