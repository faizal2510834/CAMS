DELETE FROM assets WHERE asset_id LIKE 'AST-MD%';
DELETE FROM departments WHERE department_id LIKE 'DEPT-AI%';
DELETE FROM locations WHERE location_id LIKE 'LOC-ROBO%';
DELETE FROM categories WHERE category_id LIKE 'CAT-SENSOR%';
COMMIT;
EXIT;
