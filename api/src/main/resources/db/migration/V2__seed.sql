INSERT INTO users (email, password_hash, display_name, role) VALUES
 ('owner@playground.local',   '$2y$10$COKeyTsg1Tju3WnsA9anUu6WENVj7QrU/FQlXTWFUh8W.LUwsU06C', 'Owner',   'OWNER'),
 ('cashier@playground.local', '$2y$10$COKeyTsg1Tju3WnsA9anUu6WENVj7QrU/FQlXTWFUh8W.LUwsU06C', 'Cashier', 'CASHIER');
INSERT INTO users (email, password_hash, display_name, role)
SELECT format('customer%s@playground.local', lpad(n::text,2,'0')), '$2y$10$COKeyTsg1Tju3WnsA9anUu6WENVj7QrU/FQlXTWFUh8W.LUwsU06C', format('Customer %s', lpad(n::text,2,'0')), 'CUSTOMER'
FROM generate_series(1,20) AS n;

INSERT INTO pizza_sizes (name, price_delta) VALUES ('Regular',0),('Large',6.00),('Family',12.00);
INSERT INTO crusts (name, price_delta) VALUES ('Classic',0),('Thin',0),('Stuffed',4.00);
INSERT INTO toppings (name, price_delta) VALUES
 ('Extra Cheese',2.50),('Mushroom',2.00),('Pepperoni',3.00),('Olives',1.50),
 ('Chicken',3.50),('Pineapple',1.50),('Jalapeno',1.50),('Onion',1.00);
INSERT INTO pizzas (name, description, base_price, category) VALUES
 ('Margherita','Tomato, mozzarella, basil',18.00,'classic'),
 ('Pepperoni','Pepperoni and mozzarella',22.00,'classic'),
 ('Hawaiian','Ham, pineapple, mozzarella',22.00,'classic'),
 ('Four Cheese','Mozzarella, cheddar, parmesan, blue',24.00,'classic'),
 ('BBQ Chicken','BBQ sauce, chicken, red onion',26.00,'signature'),
 ('Meat Feast','Pepperoni, beef, chicken, sausage',28.00,'signature'),
 ('Veggie Supreme','Mushroom, olives, peppers, onion',23.00,'vegetarian'),
 ('Spicy Diavola','Spicy salami, chilli, mozzarella',25.00,'signature'),
 ('Garlic Prawn','Prawns, garlic butter, rocket',29.00,'seafood'),
 ('Tuna Melt','Tuna, sweetcorn, cheddar',24.00,'seafood'),
 ('Mushroom Truffle','Mixed mushroom, truffle oil',27.00,'vegetarian'),
 ('Seasonal Special','Chef choice, ask staff',30.00,'signature');
UPDATE pizzas SET active = FALSE WHERE name = 'Seasonal Special';
