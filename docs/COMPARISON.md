# Why consider this alternative?

Bio Track focuses on source availability, local storage, explicit Health Connect glucose writes, incremental meter imports with duplicate checks, configurable units and detailed offline reports. Users can inspect the implementation, build their own APK and choose when to share stored readings. These priorities may make it a better fit for a particular workflow; they do not establish clinical superiority.

| Area | This source release | Comparison boundary |
| --- | --- | --- |
| Source and modification | MIT-licensed original project source and build instructions | No claim about rights to other products' code |
| Android sharing | Direct, user-triggered blood glucose writes to Health Connect | Not a direct Google Fit, Google Health or Zepp integration; receiving apps need their own compatible access |
| Meter imports | Community-reported Instant workflow; duplicate-resistant storage | Other models and all phone/firmware combinations are unverified |
| Reports | Date/source/tag filters, descriptive statistics, time-of-day patterns, PDF and CSV | Spot measurements cannot establish CGM time in range or clinical outcomes |
| Support and validation | Experimental community software | Not equivalent to a manufacturer's supported and validated product |

mySugr documents data import/export and connected-device features. Feature availability can vary by country, platform and version. We do not claim that it universally lacks Health Connect, that its Bluetooth implementation is defective, or that this project fixes every issue in another product. Individual experience is not a controlled comparison.

Official comparison references: [mySugr import/export](https://www.mysugr.com/support/en/can-i-import-and-export-data), [mySugr connectivity](https://www.mysugr.com/support/en/connectivity-data-import), [Android Health Connect](https://developer.android.com/health-and-fitness/health-connect/get-started). Check current documentation before making public product comparisons.
