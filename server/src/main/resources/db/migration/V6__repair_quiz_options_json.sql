-- Repair the malformed quiz option arrays written by the original seed.
-- V2 stored rows 6-30 with SQL single quotes inside the JSON array, e.g.
-- ['airport','bakery',...], which makes the quiz read endpoint fail with 500.
-- Limit the replacement to arrays that still begin with a single quote so
-- valid JSON values containing apostrophes are left untouched.
UPDATE quiz_questions
SET options = REPLACE(options, CHAR(39), CHAR(34))
WHERE options LIKE '[%'
  AND options NOT LIKE '["%';
