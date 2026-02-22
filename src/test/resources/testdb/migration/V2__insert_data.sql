-- =========================
-- Insert sample ingredients
-- =========================
INSERT INTO ingredient (name, category) VALUES
    ('Chicken Breast', 'MEAT'),
    ('Olive Oil', 'OTHER'),          -- no OIL/FAT category, so map to OTHER
    ('Garlic', 'VEGETABLES'),        -- fits best here
    ('Salt', 'SPICES'),
    ('Black Pepper', 'SPICES'),
    ('Spaghetti', 'DRY_GOODS'),      -- dry pantry staple
    ('Tomato Sauce', 'VEGETABLES'),
    ('Basil', 'VEGETABLES'),         -- fresh herb, closest is VEGETABLES
    ('Parmesan Cheese', 'DAIRY'),
    ('Rice', 'DRY_GOODS'),           -- staple dry food
    ('Broccoli', 'VEGETABLES');


-- =========================
-- Insert sample recipes
-- =========================
INSERT INTO recipe (name, portions, user_id) VALUES
    ('Garlic Chicken', 2, 1),
    ('Spaghetti Pomodoro', 4, 1),
    ('Chicken & Broccoli Rice Bowl', 3, 1);

-- =========================
-- Link recipes ↔ ingredients
-- =========================
-- Garlic Chicken
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, total_quantity, unit)
SELECT r.id, i.id, q.qty, q.unit
FROM recipe r
JOIN (VALUES
    ('Garlic Chicken', 'Chicken Breast', 500, 'GRAM'),       -- grams → GRAM
    ('Garlic Chicken', 'Garlic', 3, 'CLOVE'),                -- cloves → CLOVE
    ('Garlic Chicken', 'Olive Oil', 2, 'TABLESPOON'),        -- tbsp → TABLESPOON
    ('Garlic Chicken', 'Salt', 1, 'TEASPOON'),               -- tsp → TEASPOON
    ('Garlic Chicken', 'Black Pepper', 0.5, 'TEASPOON')      -- tsp → TEASPOON
) AS q(recipe_name, ingredient_name, qty, unit)
    ON TRUE
JOIN ingredient i ON i.name = q.ingredient_name
WHERE r.name = q.recipe_name;

-- Spaghetti Pomodoro
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, total_quantity, unit)
SELECT r.id, i.id, q.qty, q.unit
FROM recipe r
JOIN (VALUES
    ('Spaghetti Pomodoro', 'Spaghetti', 400, 'GRAM'),
    ('Spaghetti Pomodoro', 'Tomato Sauce', 500, 'MILLILITER'),
    ('Spaghetti Pomodoro', 'Garlic', 2, 'CLOVE'),
    ('Spaghetti Pomodoro', 'Olive Oil', 2, 'TABLESPOON'),
    ('Spaghetti Pomodoro', 'Basil', 5, 'LEAF'),
    ('Spaghetti Pomodoro', 'Parmesan Cheese', 50, 'GRAM')
) AS q(recipe_name, ingredient_name, qty, unit)
    ON TRUE
JOIN ingredient i ON i.name = q.ingredient_name
WHERE r.name = q.recipe_name;

-- Chicken & Broccoli Rice Bowl
INSERT INTO recipe_ingredient (recipe_id, ingredient_id, total_quantity, unit)
SELECT r.id, i.id, q.qty, q.unit
FROM recipe r
JOIN (VALUES
    ('Chicken & Broccoli Rice Bowl', 'Chicken Breast', 400, 'GRAM'),
    ('Chicken & Broccoli Rice Bowl', 'Rice', 200, 'GRAM'),
    ('Chicken & Broccoli Rice Bowl', 'Broccoli', 250, 'GRAM'),
    ('Chicken & Broccoli Rice Bowl', 'Olive Oil', 1, 'TABLESPOON'),
    ('Chicken & Broccoli Rice Bowl', 'Salt', 1, 'TEASPOON'),
    ('Chicken & Broccoli Rice Bowl', 'Black Pepper', 0.5, 'TEASPOON')
) AS q(recipe_name, ingredient_name, qty, unit)
    ON TRUE
JOIN ingredient i ON i.name = q.ingredient_name
WHERE r.name = q.recipe_name;

-- =========================
-- Insert sample meal plan
-- =========================
INSERT INTO meal_plan (start_date, end_date, user_id) VALUES
    ('2025-09-29', '2025-10-05', 1);

-- Daily plans
INSERT INTO daily_meal_plan (meal_plan_id, date)
SELECT id, d::date
FROM meal_plan, generate_series('2025-09-29'::date, '2025-10-05'::date, interval '1 day') d;

INSERT INTO daily_meals (daily_meal_plan_id, recipe_id, meal_time, servings)
SELECT dmp.id, r.id, q.meal_time, q.servings
FROM (VALUES
    ('2025-09-29'::date, 'Garlic Chicken', 'DINNER', 2),
    ('2025-09-30'::date, 'Spaghetti Pomodoro', 'DINNER', 4),
    ('2025-10-01'::date, 'Chicken & Broccoli Rice Bowl', 'DINNER', 3),
    ('2025-10-02'::date, 'Garlic Chicken', 'LUNCH', 2),
    ('2025-10-03'::date, 'Spaghetti Pomodoro', 'LUNCH', 2),
    ('2025-10-04'::date, 'Chicken & Broccoli Rice Bowl', 'DINNER', 3),
    ('2025-10-05'::date, 'Garlic Chicken', 'DINNER', 2)
) AS q(plan_date, recipe_name, meal_time, servings)
JOIN daily_meal_plan dmp ON dmp.date = q.plan_date
JOIN recipe r ON r.name = q.recipe_name;
