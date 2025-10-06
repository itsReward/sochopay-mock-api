# SoshoPay Mock API

A comprehensive mock API server for testing the SoshoPay mobile application with realistic workflows, token validation, and stateful scenarios.

## Features

✅ **Complete API Coverage**
- Authentication (OTP, PIN, Login, Logout, Token Refresh)
- Profile Management (Personal Details, Address, Documents, Next of Kin)
- Loan Operations (Cash Loans, PayGo Loans, Applications, History)
- Payment Processing (Mobile Money, Receipts, Early Payoff)

✅ **Advanced Features**
- JWT token generation with realistic expiration (15 min access, 30 days refresh)
- OTP simulation with console logging
- Stateful loan application workflows (submission → review → approval/rejection)
- Realistic payment processing with success/failure simulation
- File upload handling (profile pictures, documents)
- JSON file persistence

✅ **Production-Ready**
- Docker support for easy deployment
- CORS enabled
- Comprehensive error handling
- Request logging
- Health check endpoint

## Quick Start

### Local Development

1. **Clone and navigate to mock-api directory**
```bash
cd mock-api