package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.constants.WalletRefType;
import in.codebasket.cbwa.common.entity.WalletTransaction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long>, JpaSpecificationExecutor<WalletTransaction> {
    Optional<WalletTransaction> findByUid(String uid);

    List<WalletTransaction> findByWalletIdOrderByIdAsc(Long walletId);

    List<WalletTransaction> findByReferenceTypeAndReferenceIdOrderByIdAsc(WalletRefType referenceType, String referenceId);
}
