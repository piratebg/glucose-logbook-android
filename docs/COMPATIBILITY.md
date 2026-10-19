# Accu-Chek compatibility audit

Checked 19 October 2026 against official manufacturer/mySugr material and Bio Track 0.3.0 source. This is a documentation/code audit, not hardware testing of additional models. Product availability and mySugr connection availability vary by market. The catalogue below covers documented mySugr Bluetooth models and relevant regional/legacy meter families, not every historic Accu-Chek product ever manufactured.

## mySugr Bluetooth meter configurations

| Meter/configuration | mySugr connection | Bio Track status | Count as confirmed supported? |
| --- | --- | --- | --- |
| Instant | Built-in Bluetooth | Community-reported connection, reading values/times, repeated sync without duplicates and Health Connect export on one Android setup | Yes, on the tested unit/phone combination |
| Guide | Built-in Bluetooth | Untested; matching glucose-service/authentication/history behavior must be verified | No |
| Guide Me | Built-in Bluetooth | Untested; matching glucose-service/authentication/history behavior must be verified | No |
| Aviva Connect | Built-in Bluetooth; automatic mySugr syncing ends 28 October 2026 | Untested; not the same product as plain Aviva | No |
| Performa Connect | Built-in Bluetooth; automatic mySugr syncing ends 28 October 2026 | Untested; not the same product as plain Performa | No |
| Mobile plus Mobile Wireless Adapter | External Bluetooth adapter required; Mobile alone has no built-in Bluetooth | Untested; adapter-specific pairing/protocol behavior has not been implemented or validated separately | No |

Confirmed Bio Track meter models: 1. Additional mySugr-listed configurations not confirmed in Bio Track: 5 (four direct-Bluetooth models and one external-adapter configuration). Six manufacturer-documented mySugr Bluetooth configurations are identified globally as of the check date; two are being decommissioned by mySugr on 28 October. This does not establish what a particular regional app's Connections menu offers.

The mySugr decommissioning notice says Aviva Connect and Performa Connect stopped being manufactured in 2019. Their radio hardware does not disappear when mySugr integration ends, but that is not evidence Bio Track supports them.

## Other meter families and meter-containing managers

| Model/family | Documented connection distinction | Direct mySugr meter Bluetooth status | Bio Track automatic import |
| --- | --- | --- | --- |
| Instant S | USB; unlike Instant, lacks its Bluetooth feature | No, per model-specific manufacturer information | Not supported by current BLE-only importer |
| Active | USB/Smart Pix | Not a documented direct mySugr Bluetooth meter; manufacturer FAQ says Active is not compatible | Not supported |
| Aviva | Infrared | No direct meter Bluetooth connection documented | Not supported |
| Aviva Nano | Infrared/Smart Pix legacy meter | No direct meter Bluetooth connection documented | Not supported |
| Nano (SmartView market variant) | Infrared | No direct meter Bluetooth connection documented | Not supported |
| Performa | Infrared | No direct meter Bluetooth connection documented; distinguish Performa Connect | Not supported |
| Performa Nano | Infrared | No direct meter Bluetooth connection documented | Not supported |
| Aviva Expert | Infrared | No direct meter Bluetooth connection documented | Not supported |
| Guide Link | Bluetooth to compatible MiniMed pumps; distinct from Guide | Not listed as a direct mySugr glucose-meter connection | Not supported as a Bio Track integration |
| Aviva Combo handset | Meter/pump controller; pump Bluetooth plus infrared data path | Pump Bluetooth is not the mySugr glucose-meter interface | Not supported |
| Aviva Insight diabetes manager | Meter-containing pump manager; USB data path | No direct mySugr meter Bluetooth connection documented | Not supported |
| Aviva Solo diabetes manager | Meter-containing pump manager; USB data path | No direct mySugr meter Bluetooth connection documented | Not supported |
| Guide Solo diabetes manager | Meter-containing pump manager; USB data path | No direct mySugr meter Bluetooth connection documented | Not supported |

These are not all currently sold in Malaysia; several are legacy/regional products. SmartGuide is a CGM, not a fingerstick meter, and Bio Track does not implement its sensor protocol. Older families outside these current/support inventories are not implied to be supported. Manually entering a reading from any meter is different from automatic device compatibility.

## Bio Track implementation boundary

MeterClient.kt uses Bluetooth LE Glucose Service 0x1808, Glucose Measurement 0x2A18 notifications, optional Context 0x2A34 notifications, and RACP 0x2A52 indications/history requests. Android bonding is used. It has no per-model Aviva/Performa/Mobile adapter protocol implementation and no USB, infrared, Bluetooth Classic pump or CGM transport.

A manufacturer statement that another meter has Bluetooth/mySugr connectivity does not prove that this exact GATT/authentication/history behavior matches. No public manufacturer GATT protocol or physical captures were obtained for the five unconfirmed configurations in this audit. Do not relabel them as supported based on brand or similarity of names.

For each candidate, validation requires the exact model/firmware, service/characteristic discovery, bonding/encryption, full history retrieval, new reading reception, units/time correctness, repeated sync deduplication, interrupt/reconnect recovery and Health Connect export. The observed Instant result establishes only the tested setup.

## Sources

- Official mySugr connected meters (Guide, Instant, Guide Me, Mobile adapter, Performa Connect): https://www.mysugr.com/accuchek
- Official mySugr/Accu-Chek pairing list including Aviva Connect: https://www.accu-chek.com/support/faq/getting-started/connecting-to-mysugr
- Aviva/Performa Connect decommissioning date: https://www.mysugr.com/support/en/why-accu-chekr-avivaperforma-connect-connection-being-decommissioned
- Instant versus Instant S Bluetooth distinction: https://www.accu-chek.in/faq/product/11726
- Instant S model-specific digital compatibility: https://www.accu-chek.com.pk/support/instant-s/faq
- Active model-specific mySugr distinction: https://www.accu-chek.com.tr/destek/sss
- Manufacturer meter/manager connection inventory: https://www.accu-chek.co.uk/hcp/sites/g/files/papvje326/files/2025-01/accu-chek-care-connectivity-guide-jan-2025.pdf
- Guide Link pump interface: https://www.accu-chek.co.uk/document/accu-chek-guide-link-users-manual/download?inline=
- Aviva infrared: https://www.accu-chek.com/products/meters/aviva
- Nano infrared: https://www.accu-chek.com/document/accu-chek-nano-meter-users-manual-0/download?inline=
- Aviva Nano legacy Smart Pix source: https://www.rochediabetescareme.com/document/accu-chek-smart-pix-user-manual/download?inline=
- Aviva Combo meter/pump Bluetooth: https://www.accu-chek.co.uk/document/accu-chek-aviva-combo-handset-users-manual/download?inline=
- Malaysian product inventory: https://www.accu-chek.com.my/sitemap

This audit changes documentation only; no APK or device behavior was changed.
