# Reporting methods

Reports select 1–366 inclusive calendar days in the selected timezone and can filter by meter/manual source and meal tag. An equally long immediately preceding calendar period supplies the comparison. Invalid/ineligible values and future-dated records are excluded from statistics and counted separately. Unconfirmed timestamps are identified.

Each included reading has equal weight. Mean is the arithmetic mean; SD is population SD; median and quartiles use linear interpolation at (n−1)×p. Descriptive CV is SD/mean ×100 and requires more than one reading. Targets include both endpoints in the within-target category. Changing display units uses the protocol conversion constant; it does not change the underlying measurement.

The timeline, 24-hour overlay, hourly bins, eight 3-hour blocks, weekdays, meal tags and daily logbook describe recorded samples. A repeat pattern means above or below the configured target in the same 3-hour block on at least three distinct measured days. It is an unvalidated descriptive rule, not an alert or prediction. Repeated tests on one day do not establish recurrence. Sparse data (fewer than ten readings or three measured days) warrant particular caution.

Time-of-day grouping does not establish fasting or meal timing; meal tags are annotations. Missing measurements do not imply normal glucose. Percentages count readings, not elapsed time. No CGM AGP, GMI, estimated HbA1c or dosing advice is generated.

PDF exports include methods and context; filtered CSV exports include both units, UTC/local timestamps, timezone, source, annotations, timestamp confirmation and sharing status. Exports are sensitive and may remain after app deletion. Review them against original meter records and discuss interpretation with a care professional.
