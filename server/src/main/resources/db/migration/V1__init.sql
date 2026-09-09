-- =============================================================================
-- LinguaAI schema (V1)
-- Written to run on BOTH MySQL 8 (production) and H2 in MySQL mode (tests):
--   * no inline INDEX/KEY clauses (separate CREATE INDEX statements)
--   * no ENGINE/CHARSET/COLLATE/ENUM/UNSIGNED/ON UPDATE clauses
--   * BOOLEAN + TIMESTAMP + DATETIME only
-- =============================================================================

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    family_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_profiles (
    user_id BIGINT PRIMARY KEY,
    language_id BIGINT,
    level VARCHAR(20),
    goal VARCHAR(60),
    daily_goal_minutes INT NOT NULL DEFAULT 10,
    onboarded BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE languages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    levels VARCHAR(500) NOT NULL
);

CREATE TABLE lessons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    language_id BIGINT NOT NULL,
    level VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    type VARCHAR(20) NOT NULL,
    estimated_minutes INT NOT NULL,
    difficulty INT NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_lesson_language FOREIGN KEY (language_id) REFERENCES languages (id)
);

CREATE TABLE vocabularies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    language_id BIGINT NOT NULL,
    level VARCHAR(20) NOT NULL,
    word VARCHAR(120) NOT NULL,
    reading VARCHAR(200),
    pronunciation VARCHAR(200),
    meaning VARCHAR(500) NOT NULL,
    example TEXT,
    example_translation TEXT,
    category VARCHAR(80),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_vocab_language FOREIGN KEY (language_id) REFERENCES languages (id)
);

CREATE TABLE grammar_lessons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    language_id BIGINT NOT NULL,
    level VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    structure VARCHAR(500),
    meaning VARCHAR(1000),
    usage TEXT,
    examples TEXT,
    notes TEXT,
    difficulty INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_grammar_language FOREIGN KEY (language_id) REFERENCES languages (id)
);

CREATE TABLE quizzes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    language_id BIGINT NOT NULL,
    level VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_quiz_language FOREIGN KEY (language_id) REFERENCES languages (id)
);

CREATE TABLE quiz_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    prompt VARCHAR(1000) NOT NULL,
    options TEXT,
    correct_answer VARCHAR(500) NOT NULL,
    explanation TEXT,
    position INT NOT NULL,
    CONSTRAINT fk_question_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id)
);

CREATE TABLE quiz_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    score INT NOT NULL,
    total INT NOT NULL,
    duration_seconds INT,
    completed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_attempt_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id)
);

CREATE TABLE quiz_answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer VARCHAR(500),
    correct BOOLEAN NOT NULL,
    CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id)
);

CREATE TABLE ai_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    summary TEXT,
    summarized_until BIGINT,
    context_lesson_id BIGINT,
    context_grammar_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_conv_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE ai_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    token_count INT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
);

CREATE TABLE user_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_operation_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    ref_id BIGINT,
    minutes INT NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_progress_operation UNIQUE (user_id, client_operation_id)
);

CREATE TABLE user_vocabulary_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    vocabulary_id BIGINT NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    mastery_level INT NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    wrong_count INT NOT NULL DEFAULT 0,
    last_reviewed_at TIMESTAMP,
    next_review_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_uvp_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_uvp_vocab FOREIGN KEY (vocabulary_id) REFERENCES vocabularies (id),
    CONSTRAINT uq_uvp_user_vocab UNIQUE (user_id, vocabulary_id)
);

CREATE TABLE user_mistakes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    language_id BIGINT,
    topic VARCHAR(200) NOT NULL,
    detail TEXT,
    source VARCHAR(30),
    created_at TIMESTAMP NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_mistake_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE learning_streaks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_date DATE NOT NULL,
    minutes INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_streak_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_streak_user_date UNIQUE (user_id, activity_date)
);
