# Additional terms

My Shopping List
Copyright (C) 2026 Roberto Fichera

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. See [LICENSE](LICENSE) for the full text.

The following additional terms apply to this work under
[section 7](https://www.gnu.org/licenses/gpl-3.0.html#section7) of that licence.
Both are expressly permitted there. Neither restricts any right the licence
grants, and neither is a "further restriction" that a recipient may remove.

**(a) Trademarks — GPL v3 section 7(e).** Rights under trademark law are *not*
granted for the name **My Shopping List**, for its translations shipped in this
project, or for the **application icon** (the basket-and-checklist artwork in
`app/src/main/res/drawable/ic_launcher_foreground.xml` and
`ic_launcher_background.xml`, and the adaptive icon composed from them). These
remain reserved to the copyright holder.

**(b) Publicity — GPL v3 section 7(d).** The names of the licensor and of the
authors of this material may not be used for publicity purposes.

If you convey this work, or a work based on it, you must keep these additional
terms in place.

## What this means in practice

You are free to use, study, modify, redistribute and publish this software,
including in compiled form and including on app stores, exactly as the GNU
General Public License version 3 allows. Any such redistribution must itself be
licensed under the GPL, with source made available.

You must, however, do so under your own name and branding. A redistribution may
not present itself as "My Shopping List" and may not carry the icon shipped with
this project. Concretely, before publishing a fork:

- change `app_name` in every `app/src/main/res/values*/strings.xml`
- replace `app/src/main/res/drawable/ic_launcher_foreground.xml` and
  `ic_launcher_background.xml` with your own artwork
- change `applicationId` in `app/build.gradle.kts`

## Why these terms live in their own file

`LICENSE` is kept as the unmodified text of the GNU General Public License
version 3, so that automated licence detection identifies the project correctly
as GPL-3.0. Section 7 does not require the additional terms to sit inside that
file — only that the files they cover state them, or say where to find them. The
resources covered by term (a) carry such a notice.
