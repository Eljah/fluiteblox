# experiment.png: pitch failure analysis (OpenCV run)

## Куда экспортируются артефакты

Все диагностические PNG и `*.png.b64` экспортируются в каталог:

- `docs/diagnostics/`

## Обновлённая диагностическая цепочка в `ExperimentPitchDebugArtifactsExporter`

1. `adaptiveThreshold` (binary inv)
2. Вычитание горизонтальных/вертикальных линий става
3. Вычитание штилей + однопроходное удаление 1px линий (чтобы одиночные тонкие линии становились белыми уже на этом шаге)
4. Размывание тонких/слегка более толстых артефактов (`GaussianBlur 5x5` + rebinarize + морфологическое открытие)
5. Слияние блобов через щели: только вертикальный `MORPH_CLOSE` (для склейки половинок, разделённых вычтенной линейкой)
6. Визуализация sorted по площади на результате step5 (merged)
7. Выбор top-13 головок нот после sorted (крупные + округлые)
8. Поиск blob-кандидатов после merge
9. overlap-smaller + X-monophony

## Экспортируемые b64-артефакты


- `experiment_step4_thin_artifacts_blurred.png.b64` и `experiment_step5_blobs_merged_narrow_gaps.png.b64` должны пересобираться после изменения параметров step4/step5, чтобы визуализация соответствовала текущим порогам.
1. `experiment_step1_lines_overlay.png.b64`
2. `experiment_step2_lines_subtracted.png.b64`
3. `experiment_step3_stems_subtracted.png.b64` — после stem subtraction и дополнительного `MORPH_OPEN 2x2`, который должен съедать 1px линии за 1 прогон
4. `experiment_step4_thin_artifacts_blurred.png.b64`
5. `experiment_step5_blobs_merged_narrow_gaps.png.b64`
6. `experiment_step6_blobs_sorted_area_annotated.png.b64` — подписи rank по площади (1 = самый большой) наносятся на результат `step5`
7. `experiment_step7_noteheads_round_large_top13.png.b64` — выбор 13 головок нот после sorted как сочетание «крупный + округлый»
8. `experiment_step8_blobs_all.png.b64`
9. `experiment_step9_blobs_filtered_overlap_monophony.png.b64`

## Пороги/формулы

- Stem subtraction: `minStemHeight = max(8, round(staffSpacing * 1.6))`
- Single-pixel line cleanup on step3: `MORPH_OPEN` с ядром `2x2` (цель: полностью выбелить 1px артефакты за один проход)
- Blur thin artifacts (усилен): `GaussianBlur(step3PipelineMask, 5x5)` + `threshold=142` + `MORPH_OPEN` квадратным ядром `thinEraseSize = max(2, lineThickness + 1)` + монотонный guard `Core.max(blurRebinarized, step3PipelineMask, blurRebinarized)` (чтобы step4 не возвращал линии, уже выбеленные на step3) + финальная жёсткая бинаризация `threshold=127`
- Merge narrow gaps (берёт **точно** результат step4):
  - вертикальная склейка половинок головок: `mergeHeight = max(3, lineThickness * 2 + 1)` + kernel `Size(1, mergeHeight)` + `MORPH_CLOSE`
  - горизонтального разрастания на merge-шаге нет (kernel по X равен 1)
  - финальная бинаризация `threshold=127`
- Blob area filter: `minArea=4`, `maxArea=6000`
- Top13 round+large selection (после sorted на step5): score = `area * (0.5 + 0.5 * (1 / (1 + abs(aspect-1))))`, где `aspect = width/height`; берутся top-13

- Main recognition gate (OpenCvScoreProcessor): boundary откалиброван на `experiment.png` (`EXPERIMENT_ROUND_LARGE_BOUNDARY = 50.364708`, `(score13 + score14)/2` на step5 для experiment), но применяется с нормализацией по `staffSpacing^2`: `boundary = 50.364708 * (staffSpacing / EXPERIMENT_BASE_STAFF_SPACING)^2`. Это сохраняет инвариантность к масштабу/разрешению страницы.
- Monophony winner score:
  - `score = area - 9*sizePenalty - 12*aspectPenalty - 18*thinPenalty`

## Статистика после изменений (с фактического запуска exporter)

Логи последнего прогона:

- `Stage0(noStems) blobs: raw=37, overlapKept=37, monoKept=14`
- `Stage1(blur thin) blobs: raw=37, overlapKept=37, monoKept=14`
- `Stage2(merge narrow gaps) blobs: raw=36, overlapKept=36, monoKept=12`

Proxy-метрики распознавания относительно `experiment_red.png`:

- До новых шагов (`noStems`):
  - `redExpected=13`, `blobs=14`, `matchedExpected=11`, `misses=2`, `unmatchedBlobs=3`
- После blur:
  - `redExpected=13`, `blobs=14`, `matchedExpected=10`, `misses=3`, `unmatchedBlobs=4`
- После merge:
  - `redExpected=13`, `blobs=12`, `matchedExpected=9`, `misses=4`, `unmatchedBlobs=3`

Итого по вашему вопросу «сколько нот распознали теперь»: по текущей proxy-оценке после всех новых шагов совпало **9 из 13** ожидаемых маркеров (`matchedExpected=9`).


## Понoтный отчёт (текущее состояние)

Сформирован файл `docs/diagnostics/experiment_notewise_recognition_report.md` с понотным сопоставлением по `experiment` (note-by-note):

- текущая proxy-оценка по step5 даёт `matched=13/13`, `misses=0` (при этом остаются лишние неиспользованные blobs: `unmatched_blobs=34`).

Важно: это именно proxy-сопоставление по red-маркерам и blobs, а не полный вывод runtime `OpenCvScoreProcessor` в Android-окружении.
