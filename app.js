const express = require('express');
const bodyParser = require('body-parser');
const mongoose = require('mongoose');
const corsMiddleware = require('./middleware/cors');

const app = express();

// Middleware
app.use(bodyParser.json());
app.use(corsMiddleware);

// MongoDB connection
mongoose.connect('mongodb://localhost:27017/foodsave', {
  useNewUrlParser: true,
  useUnifiedTopology: true,
});

// Routes
app.get('/', (req, res) => {
  res.send('API is running...');
});

// Start the server
const PORT = process.env.PORT || 5000;
app.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});