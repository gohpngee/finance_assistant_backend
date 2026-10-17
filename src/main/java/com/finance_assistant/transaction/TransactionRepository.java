package com.finance_assistant.transaction;

import com.finance_assistant.transaction.enums.TransactionKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findBySourceMessageKey(String sourceMessageKey);

    @Query("""
            select coalesce(sum(case when destination.id = :accountId
                then t.amountSen else -t.amountSen end), 0)
            from Transaction t
            left join t.sourceAccount source
            left join t.destinationAccount destination
            where (source.id = :accountId or destination.id = :accountId)
              and t.deletedAt is null
              and t.transactionDate <= :asOf
            """)
    long sumAssetChange(@Param("accountId") long accountId, @Param("asOf") LocalDate asOf);

    @Query("""
            select t from Transaction t
            left join t.sourceAccount source
            left join t.destinationAccount destination
            where t.deletedAt is null
              and (:fromDate is null or t.transactionDate >= :fromDate)
              and (:toDate is null or t.transactionDate <= :toDate)
              and (:kind is null or t.kind = :kind)
              and (:accountId is null or source.id = :accountId or destination.id = :accountId)
            """)
    Page<Transaction> search(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                             @Param("kind") TransactionKind kind, @Param("accountId") Long accountId,
                             Pageable pageable);

    @Query("""
            select t from Transaction t
            where t.deletedAt is null and t.transactionDate between :fromDate and :toDate
            order by t.transactionDate, t.id
            """)
    List<Transaction> findInRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
