# RadiiMC Hospital & Death Insurance

Two Paper plugins for the RadiiMC Survival/RP server (Paper 1.21.4, Java 21).

- **HospitalPlugin**: players who die wake up in the nearest hospital within `hospital-radius` blocks, otherwise in the main hospital.
- **DeathInsurance** (needs HospitalPlugin and a Vault economy): decides what a death costs.

| Where the player wakes up | Insured | Result |
|---|---|---|
| Main hospital | either | Pays `main-hospital-flat-fee`, keeps inventory |
| Nearby hospital | yes | Pays `insurance-co-pay` (if they can), keeps inventory |
| Nearby hospital | no | Billed `payment-per-xp-level` × level. If they can afford it, the hospital holds their items until `/insurance pay` (or `/insurance abandon`) within `claim-timeout-seconds` of online time. If not, items are destroyed (`destroy-unpaid-items: false` drops them instead). |

Deaths with no hospital set, or where keepInventory already applies, are left to vanilla.

## Commands

| Command | Permission (default) |
|---|---|
| `/hospital set main` | `hospital.setmain` (op) |
| `/hospital set hospital [name]` | `hospital.sethospital` (op) |
| `/hospital remove [name]` (no name: closest) | `hospital.removehospital` (op) |
| `/hospital removemain` | `hospital.removemain` (op) |
| `/hospital list` | `hospital.list` (op) |
| `/insurance [status\|buy\|pay\|abandon]` | `insurance.use` (everyone) |
| `/insurance reload` | `insurance.admin` (op) |

Settings live in `plugins/HospitalPlugin/settings.yml` and `plugins/DeathInsurance/settings.yml`.

## Build

```sh
JAVA_HOME=<JDK 21-23> ./gradlew build
```

Jars land in `HospitalPlugin/build/libs/` and `DeathInsurancePlugin/build/libs/`. Install both.
