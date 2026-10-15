const express = require("express");
const cors = require("cors");
const Database = require("better-sqlite3");
const bcrypt = require("bcryptjs");
const crypto = require("crypto");
const path = require("path");

const app = express();
const PORT = 3000;

app.use(cors());
app.use(express.json({ limit: "1mb" }));

// DATABASE
const db = new Database(path.join(__dirname, "printxpress.db"));
db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");

// TABLES - existing database data is preserved
db.exec(`
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT NOT NULL,
    password TEXT NOT NULL,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    product TEXT NOT NULL DEFAULT 'Business Cards',
    card_size TEXT NOT NULL,
    paper_type TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    design_file TEXT,
    delivery_method TEXT NOT NULL,
    address TEXT,
    total_price REAL NOT NULL,
    status TEXT NOT NULL DEFAULT 'Pending',
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS product_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL,
    image TEXT
);

CREATE TABLE IF NOT EXISTS products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    image TEXT,
    price REAL NOT NULL,
    size TEXT NOT NULL,
    paper_type TEXT NOT NULL,
    FOREIGN KEY (category_id) REFERENCES product_categories(id)
);
`);

// SEED CATEGORIES
const insertCategory = db.prepare(`
    INSERT OR IGNORE INTO product_categories (name, description, image)
    VALUES (?, ?, ?)
`);

const categories = [
  [
    "Business Cards",
    "Professional business cards for your brand.",
    "business_cards",
  ],
  ["Flyers", "Promote your business with printed flyers.", "flyers"],
  ["Posters", "High-quality posters for events and promotions.", "posters"],
  ["Banners", "Large-format printing for promotions.", "banners"],
  ["Stickers", "Custom stickers for products and packaging.", "stickers"],
  ["T-Shirts", "Customized printed T-shirts.", "tshirts"],
];

for (const category of categories) {
  insertCategory.run(...category);
}

// SEED PRODUCTS - existing products are not duplicated
const products = [
  [
    "Standard Business Card",
    "Standard business cards for everyday professional use.",
    "business_card_standard",
    5,
    "90 x 54 mm",
    "Matte / Glossy",
    "Business Cards",
  ],
  [
    "Premium Business Card",
    "Premium business cards with a professional finish.",
    "business_card_premium",
    8,
    "90 x 54 mm",
    "Premium Matte",
    "Business Cards",
  ],
  [
    "Promotional Flyer",
    "Flyers for business advertising and promotions.",
    "flyer_promo",
    10,
    "A5",
    "Matte",
    "Flyers",
  ],
  [
    "Event Poster",
    "Posters for events and advertisements.",
    "poster_event",
    50,
    "A3",
    "Glossy",
    "Posters",
  ],
  [
    "Promotional Banner",
    "Large-format banners for promotions.",
    "banner_promo",
    500,
    "Custom Size",
    "Vinyl",
    "Banners",
  ],
  [
    "Custom Sticker",
    "Custom stickers for branding and packaging.",
    "sticker_custom",
    10,
    "Custom Size",
    "Glossy",
    "Stickers",
  ],
  [
    "Printed T-Shirt",
    "A T-shirt with a customized print design.",
    "tshirt_print",
    1500,
    "Custom Size",
    "Cotton",
    "T-Shirts",
  ],
];

const insertProduct = db.prepare(`
    INSERT INTO products
        (category_id, name, description, image, price, size, paper_type)
    SELECT id, ?, ?, ?, ?, ?, ?
    FROM product_categories
    WHERE name = ?
    AND NOT EXISTS (SELECT 1 FROM products WHERE name = ?)
`);

for (const p of products) {
  insertProduct.run(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[0]);
}

// HOME & HEALTH
app.get("/", (req, res) => {
  res.json({ message: "PrintXpress API is running!", status: "success" });
});

app.get("/api/health", (req, res) => {
  res.json({ status: "OK" });
});

// REGISTER
app.post("/api/auth/register", async (req, res) => {
  try {
    const { name, email, phone, password } = req.body;

    if (
      typeof name !== "string" ||
      typeof email !== "string" ||
      typeof phone !== "string" ||
      typeof password !== "string"
    ) {
      return res
        .status(400)
        .json({ message: "Please provide all required fields." });
    }

    const cleanName = name.trim();
    const cleanEmail = email.trim().toLowerCase();
    const cleanPhone = phone.trim();

    if (!cleanName || !cleanEmail || !cleanPhone || !password) {
      return res.status(400).json({ message: "All fields are required." });
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      return res.status(400).json({ message: "Enter a valid email address." });
    }

    if (password.length < 8) {
      return res
        .status(400)
        .json({ message: "Password must have at least 8 characters." });
    }

    const existing = db
      .prepare("SELECT id FROM users WHERE email = ?")
      .get(cleanEmail);

    if (existing) {
      return res
        .status(409)
        .json({ message: "This email is already registered." });
    }

    const hashedPassword = await bcrypt.hash(password, 10);

    const result = db
      .prepare(
        `
            INSERT INTO users (name, email, phone, password)
            VALUES (?, ?, ?, ?)
        `,
      )
      .run(cleanName, cleanEmail, cleanPhone, hashedPassword);

    return res.status(201).json({
      message: "Account created successfully!",
      user: {
        id: Number(result.lastInsertRowid),
        name: cleanName,
        email: cleanEmail,
        phone: cleanPhone,
      },
    });
  } catch (error) {
    console.error("Registration error:", error);
    return res
      .status(500)
      .json({ message: "Registration failed. Please try again." });
  }
});

// CUSTOMER LOGIN
app.post("/api/auth/login", async (req, res) => {
  try {
    const { email, password } = req.body;

    if (typeof email !== "string" || typeof password !== "string") {
      return res
        .status(400)
        .json({ message: "Email and password are required." });
    }

    const user = db
      .prepare(
        `
            SELECT id, name, email, phone, password
            FROM users WHERE email = ?
        `,
      )
      .get(email.trim().toLowerCase());

    if (!user || !(await bcrypt.compare(password, user.password))) {
      return res.status(401).json({ message: "Invalid email or password." });
    }

    return res.json({
      message: "Login successful!",
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        phone: user.phone,
      },
    });
  } catch (error) {
    console.error("Login error:", error);
    return res.status(500).json({ message: "Login failed. Please try again." });
  }
});

// CREATE ORDER
app.post("/api/orders", (req, res) => {
  try {
    const {
      user_id,
      product_id,
      card_size,
      paper_type,
      quantity,
      design_file,
      delivery_method,
      address,
    } = req.body;

    const userId = Number(user_id);
    const productId = Number(product_id);
    const qty = Number(quantity);

    if (!Number.isSafeInteger(userId) || userId <= 0) {
      return res.status(400).json({ message: "Valid user ID is required." });
    }

    if (!db.prepare("SELECT id FROM users WHERE id = ?").get(userId)) {
      return res
        .status(404)
        .json({ message: "User not found. Please log in again." });
    }

    if (!Number.isSafeInteger(productId) || productId <= 0) {
      return res
        .status(400)
        .json({ message: "Please select a valid product." });
    }

    const product = db
      .prepare(
        `
            SELECT id, name, price, size, paper_type
            FROM products WHERE id = ?
        `,
      )
      .get(productId);

    if (!product) {
      return res.status(404).json({ message: "Product not found." });
    }

    if (!Number.isSafeInteger(qty) || qty <= 0 || qty > 100000) {
      return res
        .status(400)
        .json({ message: "Quantity must be between 1 and 100000." });
    }

    if (!["Delivery", "Pickup"].includes(delivery_method)) {
      return res.status(400).json({ message: "Invalid delivery method." });
    }

    let cleanAddress = null;

    if (delivery_method === "Delivery") {
      if (typeof address !== "string" || !address.trim()) {
        return res
          .status(400)
          .json({ message: "Delivery address is required." });
      }
      cleanAddress = address.trim();
    }

    const size =
      typeof card_size === "string" && card_size.trim()
        ? card_size.trim()
        : product.size;

    const paper =
      typeof paper_type === "string" && paper_type.trim()
        ? paper_type.trim()
        : product.paper_type;

    const design = typeof design_file === "string" ? design_file.trim() : null;
    const total = Number((product.price * qty).toFixed(2));

    const result = db
      .prepare(
        `
            INSERT INTO orders (
                user_id, product, card_size, paper_type, quantity,
                design_file, delivery_method, address, total_price, status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'Pending')
        `,
      )
      .run(
        userId,
        product.name,
        size,
        paper,
        qty,
        design,
        delivery_method,
        cleanAddress,
        total,
      );

    return res.status(201).json({
      message: "Order placed successfully!",
      order: {
        id: Number(result.lastInsertRowid),
        user_id: userId,
        product_id: product.id,
        product: product.name,
        card_size: size,
        paper_type: paper,
        quantity: qty,
        unit_price: product.price,
        total_price: total,
        delivery_method,
        address: cleanAddress,
        status: "Pending",
      },
    });
  } catch (error) {
    console.error("Create order error:", error);
    return res
      .status(500)
      .json({ message: "Could not place order. Please try again." });
  }
});

// CUSTOMER: MY ORDERS
app.get("/api/orders/:userId", (req, res) => {
  try {
    const userId = Number(req.params.userId);

    if (!Number.isSafeInteger(userId) || userId <= 0) {
      return res.status(400).json({ message: "Invalid user ID." });
    }

    if (!db.prepare("SELECT id FROM users WHERE id = ?").get(userId)) {
      return res.status(404).json({ message: "User not found." });
    }

    const orders = db
      .prepare(
        `
            SELECT id, product, card_size, paper_type, quantity,
                   design_file, delivery_method, address,
                   total_price, status, created_at
            FROM orders WHERE user_id = ?
            ORDER BY id DESC
        `,
      )
      .all(userId);

    return res.json({ message: "Orders retrieved successfully.", orders });
  } catch (error) {
    console.error("Get orders error:", error);
    return res.status(500).json({ message: "Could not retrieve orders." });
  }
});

// CUSTOMER: CANCEL ORDER
app.post("/api/orders/:id/cancel", (req, res) => {
  try {
    const orderId = Number(req.params.id);
    const userId = Number(req.body && req.body.user_id);

    if (
      !Number.isSafeInteger(orderId) ||
      orderId <= 0 ||
      !Number.isSafeInteger(userId) ||
      userId <= 0
    ) {
      return res.status(400).json({
        message: "Valid order and user IDs are required.",
      });
    }

    const order = db
      .prepare("SELECT id, user_id, status FROM orders WHERE id = ?")
      .get(orderId);

    if (!order || order.user_id !== userId) {
      return res.status(404).json({
        message: "Order not found.",
      });
    }

    // Only pending orders can be cancelled.
    if (order.status !== "Pending") {
      return res.status(400).json({
        message: "Only pending orders can be cancelled.",
      });
    }

    const result = db
      .prepare(
        "UPDATE orders SET status = 'Cancelled' WHERE id = ? AND user_id = ? AND status = 'Pending'",
      )
      .run(orderId, userId);

    if (result.changes === 0) {
      return res.status(409).json({
        message: "The order status has changed. Please refresh your orders.",
      });
    }

    return res.json({
      message: "Order cancelled successfully.",
      order: { id: orderId, status: "Cancelled" },
    });
  } catch (error) {
    console.error("Cancel order error:", error);
    return res.status(500).json({
      message: "Could not cancel the order.",
    });
  }
});

// PRODUCT CATEGORIES
app.get("/api/products/categories", (req, res) => {
  try {
    const categories = db
      .prepare(
        `
            SELECT id, name, description, image
            FROM product_categories ORDER BY id
        `,
      )
      .all();

    return res.json({
      message: "Categories retrieved successfully.",
      categories,
    });
  } catch (error) {
    console.error("Categories error:", error);
    return res.status(500).json({ message: "Could not load categories." });
  }
});

// PRODUCTS - optional categoryId filter
app.get("/api/products", (req, res) => {
  try {
    const categoryId = req.query.categoryId;
    let result;

    const query = `
            SELECT p.id, p.category_id, c.name AS category_name,
                   p.name, p.description, p.image, p.price, p.size, p.paper_type
            FROM products p
            JOIN product_categories c ON c.id = p.category_id
        `;

    if (categoryId !== undefined) {
      const id = Number(categoryId);

      if (!Number.isSafeInteger(id) || id <= 0) {
        return res.status(400).json({ message: "Invalid category ID." });
      }

      result = db
        .prepare(query + " WHERE p.category_id = ? ORDER BY p.id")
        .all(id);
    } else {
      result = db.prepare(query + " ORDER BY p.id").all();
    }

    return res.json({
      message: "Products retrieved successfully.",
      products: result,
    });
  } catch (error) {
    console.error("Products error:", error);
    return res.status(500).json({ message: "Could not load products." });
  }
});

// SINGLE PRODUCT
app.get("/api/products/:id", (req, res) => {
  try {
    const id = Number(req.params.id);

    if (!Number.isSafeInteger(id) || id <= 0) {
      return res.status(400).json({ message: "Invalid product ID." });
    }

    const product = db
      .prepare(
        `
            SELECT p.id, p.category_id, c.name AS category_name,
                   p.name, p.description, p.image, p.price, p.size, p.paper_type
            FROM products p
            JOIN product_categories c ON c.id = p.category_id
            WHERE p.id = ?
        `,
      )
      .get(id);

    if (!product) {
      return res.status(404).json({ message: "Product not found." });
    }

    return res.json({ message: "Product retrieved successfully.", product });
  } catch (error) {
    console.error("Product detail error:", error);
    return res.status(500).json({ message: "Could not load product." });
  }
});

// ==========================================
// ADMIN API - DEMO AUTHENTICATION
// ==========================================
// Set ADMIN_PASSWORD in the backend environment.
// This in-memory session is for a local assignment demo only.

const adminSessions = new Map();
const loginAttempts = new Map();
const ADMIN_SESSION_MS = 8 * 60 * 60 * 1000;

app.post("/api/admin/login", (req, res) => {
  const configuredPassword =
    process.env.ADMIN_PASSWORD || "PrintXpressAdmin2026!";

  if (!configuredPassword || configuredPassword.length < 12) {
    return res.status(503).json({
      message: "Set ADMIN_PASSWORD (12+ characters) on the backend first.",
    });
  }

  const key = req.ip;
  const attempt = loginAttempts.get(key) || { count: 0, until: 0 };

  if (attempt.until > Date.now()) {
    return res
      .status(429)
      .json({ message: "Too many attempts. Try again later." });
  }

  const suppliedPassword =
    req.body && typeof req.body.password === "string" ? req.body.password : "";

  const suppliedHash = crypto
    .createHash("sha256")
    .update(suppliedPassword)
    .digest();
  const expectedHash = crypto
    .createHash("sha256")
    .update(configuredPassword)
    .digest();

  if (!crypto.timingSafeEqual(suppliedHash, expectedHash)) {
    const count = attempt.count + 1;

    loginAttempts.set(key, {
      count,
      until: count >= 5 ? Date.now() + 15 * 60 * 1000 : 0,
    });

    return res.status(401).json({ message: "Incorrect admin password." });
  }

  loginAttempts.delete(key);

  const token = crypto.randomBytes(32).toString("hex");
  adminSessions.set(token, Date.now() + ADMIN_SESSION_MS);

  return res.json({
    message: "Admin login successful.",
    token,
    expires_in_seconds: ADMIN_SESSION_MS / 1000,
  });
});

// ADMIN AUTH MIDDLEWARE
function requireAdmin(req, res, next) {
  const authorization = req.get("Authorization") || "";
  const match = /^Bearer ([a-f0-9]{64})$/.exec(authorization);
  const token = match && match[1];
  const expires = token && adminSessions.get(token);

  if (!expires || expires < Date.now()) {
    if (token) adminSessions.delete(token);
    return res.status(401).json({ message: "Admin login required." });
  }

  next();
}

// ADMIN: ALL ORDERS + SUMMARY
app.get("/api/admin/orders", requireAdmin, (req, res) => {
  try {
    const orders = db
      .prepare(
        `
            SELECT o.id, o.user_id,
                   u.name AS customer_name,
                   u.email AS customer_email,
                   u.phone AS customer_phone,
                   o.product, o.card_size, o.paper_type, o.quantity,
                   o.design_file, o.delivery_method, o.address,
                   o.total_price, o.status, o.created_at
            FROM orders o
            JOIN users u ON u.id = o.user_id
            ORDER BY o.id DESC
        `,
      )
      .all();

    const summary = {
      total: orders.length,
      pending: orders.filter((o) => o.status === "Pending").length,
      processing: orders.filter((o) => o.status === "Processing").length,
      completed: orders.filter((o) => o.status === "Completed").length,
    };

    return res.json({ summary, orders });
  } catch (error) {
    console.error("Admin orders error:", error);
    return res.status(500).json({ message: "Could not load admin orders." });
  }
});

// ADMIN: UPDATE ORDER STATUS
app.patch("/api/admin/orders/:id/status", requireAdmin, (req, res) => {
  const id = Number(req.params.id);
  const status = req.body && req.body.status;

  if (!Number.isSafeInteger(id) || id <= 0) {
    return res.status(400).json({ message: "Invalid order ID." });
  }

  if (!["Pending", "Processing", "Completed"].includes(status)) {
    return res.status(400).json({
      message: "Status must be Pending, Processing, or Completed.",
    });
  }

  try {
    const result = db
      .prepare("UPDATE orders SET status = ? WHERE id = ?")
      .run(status, id);

    if (result.changes === 0) {
      return res.status(404).json({ message: "Order not found." });
    }

    return res.json({
      message: "Order status updated successfully.",
      order: { id, status },
    });
  } catch (error) {
    console.error("Update status error:", error);
    return res.status(500).json({ message: "Could not update status." });
  }
});

// START SERVER
app.listen(PORT, "0.0.0.0", () => {
  console.log(`PrintXpress API running on port ${PORT}`);
});
