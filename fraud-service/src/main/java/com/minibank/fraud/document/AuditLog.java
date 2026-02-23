package com.minibank.fraud.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Elasticsearch document for audit trail.
 * All transaction events are indexed here for advanced search capabilities.
 * 
 * Demonstrates: Elastic & Other Non-Relational DB requirement.
 */
@Document(indexName = "audit-logs")
@Setting(shards = 1, replicas = 0)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String transactionId;

    @Field(type = FieldType.Keyword)
    private String referenceNo;

    @Field(type = FieldType.Keyword)
    private String fromAccountId;

    @Field(type = FieldType.Keyword)
    private String toAccountId;

    @Field(type = FieldType.Double)
    private BigDecimal amount;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String fraudCheckResult;

    @Field(type = FieldType.Integer)
    private int riskScore;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String details;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String eventType;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime timestamp;

    @Field(type = FieldType.Keyword)
    private String riskLevel;
}
