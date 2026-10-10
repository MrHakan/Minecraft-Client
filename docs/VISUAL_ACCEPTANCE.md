# Visual acceptance

What a release looks like, checked the same way every time. Part of it is automatic and runs in CI
with the other client game tests; part of it needs a person, and is marked **Human approval
(İnsan onayı)**. Both work from the same screenshots, so the person is looking at exactly the frames
the automatic checks passed.

## Where the screenshots are

`VisualAcceptanceGameTest` writes every frame it checks as
`build/run/clientGameTest/screenshots/NNNN_visual-*.png`. CI uploads them as the
`visual-acceptance` artifact of each run, for pull requests and `main` alike. Locally,
`./gradlew runClientGameTest` (or `tools/smoke-client.sh` on Linux) produces the same files.

Nothing is compared against a stored image. A software rasteriser does not draw the same pixels on
every Mesa build or window size, so each check compares the client against itself or against
arithmetic; see [CLIENT_GAME_TESTS.md](CLIENT_GAME_TESTS.md#render-modules-without-reference-images).

## The list

| # | Area | Screenshots | Automatic check | Human approval (İnsan onayı) |
| --- | --- | --- | --- | --- |
| 1 | ClickGUI, Default Dark | `visual-clickgui-default-dark-x1` … `-x4` | Every visible widget inside the window; at least 20 pixels in the theme's accent colour | Panels, text and buttons read clearly at every scale; nothing clipped |
| 2 | ClickGUI, AMOLED | `visual-clickgui-amoled-x*` | As 1; darker than Light | Black really is black; muted text still legible |
| 3 | ClickGUI, Light | `visual-clickgui-light-x*` | As 1; the brightest of the four at every scale | Text contrast on white panels; **known finding:** the search box stays vanilla black, decide whether that is acceptable |
| 4 | ClickGUI, high contrast | `visual-clickgui-high-contrast-x*` | As 1, with the accent the high-contrast palette forces (yellow) | Pure black surfaces, white text, yellow accent; no theme colour leaking through |
| 5 | GUI scales | every `-xN` above | The scales the window allows (1 to 3 at 1280x720, 4 needs a larger window); a scale the window refuses is reported, not shot twice | Layout holds at the smallest and largest scale |
| 6 | ESP box geometry | `visual-esp-off`, `visual-esp-off-2`, `visual-esp-box` | The box drawn around a stand six blocks ahead matches its bounding box projected through the camera's own position and field of view, within max(4 px, 0.6% of the width); the two "off" frames show the scene held still | Line weight and colour look right |
| 7 | ESP label | `visual-esp-label` | Above the box and centred on it | Readable; distance text sensible |
| 8 | Nametag geometry | `visual-nametag` | Above the target and centred on it | Readable; not overlapping the box |
| 9 | TargetHUD face layer | `visual-targethud` | The patch where the card reports drawing the face holds at least four distinct colours (a skin, not a flat card or a missing texture) | The face is the target's, the hat layer sits over it, and the card's text clears it. Hat layer correctness needs a skin with a hat layer, which the default skin lacks |

## Running a human review

1. Open the `visual-acceptance` artifact of the CI run for the commit being released (or run the
   game tests locally).
2. Walk the table top to bottom, looking only at the listed frames.
3. Record anything off as an issue naming the screenshot and the row. A row stays open until a
   later run's frames pass it.

The automatic column is enforced by the test and fails the build. The human column is not; it is a
checklist for the person preparing a release, and its findings become ordinary issues.
