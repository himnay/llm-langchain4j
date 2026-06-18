CREATE TABLE contacts (
      id SERIAL PRIMARY KEY,
      name VARCHAR(100) NOT NULL,
      email VARCHAR(150) NOT NULL,
      city VARCHAR(50) NOT NULL
);