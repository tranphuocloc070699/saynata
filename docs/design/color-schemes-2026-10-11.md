# Colour schemes for Saynata, 2026-10-11

## Summary

- The Claude look is cream background + clay-orange accent. To be "a little different", change the **hue family** of the background and the accent. Keep the layout, serif meaning text, and round soft buttons.
- Three options: **A Sage and Teal** (green-grey, calm like a study), **B Paper and Indigo** (cool grey-lilac, focused), **C Fog and Plum** (neutral with a berry accent, most distinct).
- All text pairs pass WCAG AA (4.5:1) in light and dark. Ratios are in the tables below.
- Recommendation: **A Sage and Teal**. It is the furthest from "Claude" while staying the quietest.

## 1. References

Hex values are **estimated by eye** from the Mobbin preview images (I did not sample pixels). Treat them as the nearest hex, not exact.

| App | Screen | Background | Card / surface | Text | Accent | Mood |
|---|---|---|---|---|---|---|
| Claude (baseline) | [Chat](https://mobbin.com/screens/91c85991-6bcd-4a78-85ae-a0157c542bc5), [Home](https://mobbin.com/screens/0c723473-1bab-4bba-8786-10abe4b1d5a2) | #F9F9F7 | #FFFFFF | #1F1E1D | clay #C4694F | Warm, literary. This is what we want to move away from. |
| Vocabulary | [Word detail](https://mobbin.com/screens/935d4833-e999-4cb0-92df-033c996fc00d) | #FFFFFF | #F0F0F0 (placeholder bars) | #1A1A1A, serif headword | sage-teal #9CBDBA (button) | Calm and bookish. Muted sage on white. Closest to our job. |
| Speak | [Dictionary sheet](https://mobbin.com/screens/6cda687c-0218-4908-9d4c-86fa22cfc242), [Speaking](https://mobbin.com/screens/9147c71a-e8a8-4b45-bb41-d8fa27a6cf46) | #F2F2F2 | #FFFFFF, grey-fill block #F0F0F0 | #1A1A1A, grey #8A8A8A | blue #1F4FD8 | Neutral and serious. Blue links, grey "about" block. Good model for a meaning block. |
| Babbel | [Listen and repeat (correct)](https://mobbin.com/screens/56c3b73e-2874-4a87-a58c-bb20646c2a2e) | #FFFFFF, sheet #F8F5F0 | pale green #F0FAEA | #222222 | orange #F26B1D, success green #5DB544 | Friendly. Pale green fill with a green border for "correct" is a good pattern. |
| Duolingo | [Word card](https://mobbin.com/screens/825f56d8-92e5-48b1-9d1f-a09a2986c08e), [Select image](https://mobbin.com/screens/29316184-d3f6-4c7e-86d9-7283d5dc7038) | #FDFDFD | #FFFFFF with #E5E5E5 border | #4B4B4B | blue #1CB0F6, green #58CC02 | Playful. Too loud and childish for us. Use only as a counter-example. |
| Apple Books | [Themes](https://mobbin.com/screens/7dad1a59-4e82-4565-bc00-f251ced307a4) | Calm theme #EADFC8, Paper #EFEFEF | #FFFFFF | #1A1A1A, serif | none (black) | Reading comfort. Shows that tinted paper colours are normal for reading. |
| Headspace | [Today](https://mobbin.com/screens/1cba404f-413c-4a8b-be36-d791e9ca4fed) | #FFFFFF | warm grey #F5F2EE card | #2B2B2B | orange #F4772E, green check #1E8C45 | Soft and kind. Warm grey cards with a hairline. Orange is again a Claude-like warm. |
| Todoist | [Inbox](https://mobbin.com/screens/fae19fe7-6408-49a0-b854-7df1e9d73e47) | #F6F5F2 | #FFFFFF with soft shadow | #202020 | red-brown #C4452C, purple dates #6B3FA0 | Quiet "paper and card" layout. Small purple used for meta text is a nice touch. |
| Notion | [Home](https://mobbin.com/screens/4c66fe68-6eda-4495-84c6-c7829f5e95ae), [Layout](https://mobbin.com/screens/4ad41221-d2e4-4534-bd0b-855f4b939eb8) | #F7F7F5 | #FFFFFF | #1F1F1F | blue #2383E2 | Neutral, round icon buttons with a soft shadow. Same pattern as ours. |
| Asana | [Board](https://mobbin.com/screens/e8502c9c-3cea-422b-8434-0e871f4c4d71) | #F6F6F8 | #FFFFFF | #1E1F21 | lavender #A28CE8 | A pale lilac that still feels professional. Inspires option B. |
| Elevate | [Score](https://mobbin.com/screens/d370ed5f-ee5b-41a8-8a30-ebeddc95625d), [Result](https://mobbin.com/screens/810ec22b-6493-466a-83c4-4958bf46d4c9) | dark #26262B | #2F2F36 | #F2F2F2 | pink #E0558A, violet #7C6BDB, blue #4AB8F0 | Dark, adult, scores in one colour. Inspires option C and the dark score view. |
| Waking Up | [Daily quote](https://mobbin.com/screens/17f552f8-7a09-4137-a21d-463234c96f4b) | #F4F4F4 | gradient rust #B5532A | #1B2340 (navy) | rust | Navy ink instead of black gives character. Used in option B. |

Not found on Mobbin iOS in these searches: Bear, Day One, Kindle, Things, Notion Calendar, Readwise (no result or off-topic). I used Todoist, Notion, and Apple Books instead.

What the references teach:
- Nearly all calm apps use a **near-white, slightly tinted background** (#F2-#F7) and a **pure white card**. Our current design already does this.
- The accent is used in **one small place** (a button, a link, a check). Quiet apps never use it for big areas.
- A **grey or tinted fill block** inside a white card (Speak) is the standard way to mark "the meaning". Do not use a border.
- Success and error are always **green and red-brown**, soft fill plus darker text (Babbel). Keep that, as users expect it.

## 2. Palette options

Contrast is the WCAG ratio ([WCAG 2.2, 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)). Body text needs 4.5:1. I calculated all ratios with the standard relative-luminance formula. "Meaning block" is the tinted box that holds the serif meaning text.

### Option A: Sage and Teal

Idea: a quiet green-grey study room. From Vocabulary's sage button, Headspace's soft cards, and Todoist's paper-and-card layout.

| Role | Light | Dark |
|---|---|---|
| Background | #F3F5F1 | #121715 |
| Surface / card | #FFFFFF | #1B221F |
| Meaning block | #E8EEE9 | #232C28 |
| Hairline | #D9E0DA | #2E3833 |
| Chip fill | #DCEBE4 | #24382F |
| Text primary | #1F2A26 | #E8EEEA |
| Text secondary | #4A5A53 | #B5C2BB |
| Text muted | #5F6E67 | #8A9992 |
| Accent (level chip, send button, focus) | #2F6F62 | #6FC2AE |
| Success | #2E7D4F | #6CC28A |
| Error | #B3402F | #E58A7A |

Contrast: primary text on background 13.5 / 15.4 (light / dark); primary on meaning block 12.6 / 12.2; secondary on background 6.6 / 9.8; secondary on meaning block 6.2 / 7.8; muted on background 4.9 / 6.1; accent text on chip 4.8 / 5.9; white icon on accent button 5.9 (light), dark icon on accent button 9.4 (dark); success on background 4.6 / 8.4; error on background 5.2 / 7.1.

### Option B: Paper and Indigo

Idea: cool paper and ink, like a notebook. From Speak's cool grey and blue, Asana's lilac, and Waking Up's navy ink.

| Role | Light | Dark |
|---|---|---|
| Background | #F4F4F8 | #14141C |
| Surface / card | #FFFFFF | #1D1D28 |
| Meaning block | #ECEBF6 | #262634 |
| Hairline | #DDDDE8 | #32323F |
| Chip fill | #E3E1F7 | #2D2B52 |
| Text primary | #1D1C2B | #EDEDF5 |
| Text secondary | #4B4A63 | #B9B8CC |
| Text muted | #666580 | #8F8EA6 |
| Accent | #4B47B5 | #A5A2F0 |
| Success | #2E7D4F | #6CC28A |
| Error | #B3402F | #EF8F8F |

Contrast: primary on background 15.3 / 15.7; primary on meaning block 14.2 / 12.8; secondary on background 7.8 / 9.4; secondary on meaning block 7.2 / 7.7; muted on background 5.1 / 5.7; accent on chip 5.7 / 5.7; white icon on accent 7.3 (light), dark icon on accent 8.5 (dark); success 4.6 / 8.5; error 5.2 / 7.8.

### Option C: Fog and Plum

Idea: a neutral, slightly rosy grey with a berry accent. Most different from Claude. From Elevate's pink and violet and Todoist's soft cards, but toned down.

| Role | Light | Dark |
|---|---|---|
| Background | #F7F5F7 | #171318 |
| Surface / card | #FFFFFF | #211B23 |
| Meaning block | #F0EAF1 | #2B232D |
| Hairline | #E3DCE4 | #3A303D |
| Chip fill | #EBDDEA | #3E2A3A |
| Text primary | #26202A | #F0EAF1 |
| Text secondary | #54495A | #C4B8C8 |
| Text muted | #6F6475 | #9A8E9F |
| Accent | #8A3A6E | #E08BC0 |
| Success | #2E7D4F | #6CC28A |
| Error | #B3402F | #EF8F8F |

Contrast: primary on background 14.6 / 15.5; primary on meaning block 13.4 / 12.8; secondary on background 7.8 / 9.7; secondary on meaning block 7.2 / 8.0; muted on background 5.2 / 5.9; accent on chip 5.5 / 5.4; white icon on accent 7.2 (light), dark icon on accent 8.1 (dark); success 4.7 / 8.5; error 5.2 / 7.9.

Notes for all three:
- Success and error are shared on purpose. Learners read green and red fast. Error is a brick red, not a pure red, so it stays calm. Never rely on colour alone: also mark wrong words with underline or an icon ([HIG: Color](https://developer.apple.com/design/human-interface-guidelines/color)).
- In dark mode the accent is lighter, so the send button icon must be dark (#0B0B0B), not white.
- Keep the same shadows idea as now (very soft, low opacity) in light mode. In dark mode, drop shadows and use the hairline instead.
- In SwiftUI, define each role in an asset catalog Color Set with "Any" and "Dark" values, so the system switches them for you.

## 3. Recommendation

Choose **A, Sage and Teal**. Green-grey is the least like Claude's cream and clay, and it matches the references closest to our task (Vocabulary, Headspace). It also feels calm and grown-up, not childish like Duolingo. Green is a good colour for learning and it sits well with the serif meaning text. The teal accent is clearly different from the green "correct" colour (#2E7D4F), but if you find them too close in the speaking score, switch the accent to B's indigo (#4B47B5). B is the safe second choice. C is the boldest, but a plum accent can feel fashion-like and may fight with red error marks.

## Next steps

1. Pick one option, then add Color Sets to the asset catalog (light and dark).
2. Replace the cream and clay values in the current word screen and check the meaning block and send button.
3. Test in dark mode and with Dynamic Type, then check the speaking score colours on a real device.

I did not change any app code.
