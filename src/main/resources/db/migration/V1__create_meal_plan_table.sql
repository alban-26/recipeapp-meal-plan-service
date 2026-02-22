-- =========================
-- Recipe metadata
-- =========================
CREATE TABLE IF NOT EXISTS recipe
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    portions INTEGER NOT NULL,
    user_id VARCHAR NOT NULL,
    CONSTRAINT unique_user_recipe UNIQUE (name, user_id)
);

-- =========================
-- Master ingredient dictionary
-- =========================
CREATE TABLE IF NOT EXISTS ingredient
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(255) NOT NULL
);

-- =========================
-- Recipe ↔ Ingredient link (with quantities)
-- =========================
CREATE TABLE IF NOT EXISTS recipe_ingredient
(
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
    ingredient_id BIGINT NOT NULL REFERENCES ingredient(id) ON DELETE CASCADE,
    total_quantity DOUBLE PRECISION NOT NULL,
    unit VARCHAR(50) NOT NULL,
    UNIQUE (recipe_id, ingredient_id)
);

CREATE TABLE IF NOT EXISTS meal_plan
(
    id BIGSERIAL PRIMARY KEY,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    user_id VARCHAR NOT NULL
);

CREATE TABLE IF NOT EXISTS daily_meal_plan
(
    id BIGSERIAL PRIMARY KEY,
    meal_plan_id BIGINT NOT NULL REFERENCES meal_plan(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    UNIQUE (meal_plan_id, date)
);

CREATE TABLE IF NOT EXISTS daily_meals
(
    id BIGSERIAL PRIMARY KEY,
    daily_meal_plan_id BIGINT NOT NULL REFERENCES daily_meal_plan(id) ON DELETE CASCADE,
    recipe_id BIGINT NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
    meal_time VARCHAR(50) NOT NULL,
    servings INTEGER NOT NULL,
    UNIQUE (recipe_id, daily_meal_plan_id, meal_time)
);
