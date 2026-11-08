import express from "express";
import helmet from "helmet";
import cors from "cors";
import rateLimit from "express-rate-limit";
import pg from "pg";
import argon2 from "argon2";
import { SignJWT, jwtVerify } from "jose";
const { Pool } = pg;
const app = express();
const port = Number(process.env.PORT || 8080);
for (const key of ["DATABASE_URL","JWT_SECRET"]) if (!process.env[key]) { console.error("Missing required environment variable: "+key); process.exit(1); }
if (Buffer.byteLength(process.env.JWT_SECRET) < 32) { console.error("JWT_SECRET must contain at least 32 bytes"); process.exit(1); }
const pool = new Pool({connectionString:process.env.DATABASE_URL,ssl:process.env.DATABASE_SSL==="true"?{rejectUnauthorized:true}:undefined,max:10,idleTimeoutMillis:30000,connectionTimeoutMillis:5000});
const jwtKey = new TextEncoder().encode(process.env.JWT_SECRET);
const issuer = process.env.JWT_ISSUER || "my-services";
const origins = (process.env.CORS_ORIGINS||"").split(",").map(x=>x.trim()).filter(Boolean);
app.disable("x-powered-by"); app.use(helmet());
app.use(cors({origin(origin,cb){if(!origin||origins.includes(origin))return cb(null,true);return cb(new Error("Origin is not allowed by CORS"));},methods:["GET","POST","OPTIONS"],allowedHeaders:["Content-Type","Authorization"]}));
app.use(express.json({limit:"32kb"}));
app.use("/v1/auth",rateLimit({windowMs:900000,limit:20,standardHeaders:true,legacyHeaders:false}));
const wrap=fn=>(req,res,next)=>Promise.resolve(fn(req,res,next)).catch(next);
const emailOk=v=>typeof v==="string"&&v.length<=254&&/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(v);
const usernameOk=v=>typeof v==="string"&&/^[a-zA-Z0-9_]{3,20}$/.test(v);
async function requireAuth(req,res,next){try{const h=req.get("authorization")||"";if(!h.startsWith("Bearer "))return res.status(401).json({error:"authentication_required"});const {payload}=await jwtVerify(h.slice(7),jwtKey,{issuer,audience:"my-services-api"});const q=await pool.query("SELECT id,email,username,status,email_verified FROM users WHERE id=$1",[payload.sub]);const u=q.rows[0];if(!u||u.status!=="active"||!u.email_verified)return res.status(401).json({error:"account_unavailable"});req.user=u;next();}catch{return res.status(401).json({error:"invalid_or_expired_token"});}}
app.get("/health",wrap(async(_req,res)=>{await pool.query("SELECT 1");res.json({status:"ok",service:"MY SERVICES",component:"My Launcher Core",version:"0.1.0"});}));
app.get("/v1/services",wrap(async(_req,res)=>{const q=await pool.query("SELECT service_key,display_name,description,api_version,enabled FROM service_catalog WHERE enabled=true ORDER BY sort_order");res.json({services:q.rows});}));
app.post("/v1/auth/register",wrap(async(req,res)=>{if(process.env.ALLOW_REGISTRATION!=="true")return res.status(503).json({error:"registration_temporarily_disabled",message:"Registration stays disabled until email verification and abuse protection are configured."});const {email,username,password}=req.body||{};if(!emailOk(email)||!usernameOk(username)||typeof password!=="string"||password.length<12||password.length>128)return res.status(400).json({error:"invalid_registration_data"});const hash=await argon2.hash(password,{type:argon2.argon2id,memoryCost:19456,timeCost:2,parallelism:1});try{await pool.query("INSERT INTO users(email,username,password_hash) VALUES($1,$2,$3)",[email.toLowerCase(),username,hash]);res.status(202).json({status:"verification_required"});}catch(e){if(e.code==="23505")return res.status(409).json({error:"email_or_username_unavailable"});throw e;}}));
app.post("/v1/auth/login",wrap(async(req,res)=>{const {email,password}=req.body||{};if(!emailOk(email)||typeof password!=="string"||password.length>128)return res.status(400).json({error:"invalid_credentials"});const q=await pool.query("SELECT id,email,username,password_hash,email_verified,status FROM users WHERE email=$1",[email.toLowerCase()]);const u=q.rows[0];if(!u||u.status!=="active"||!await argon2.verify(u.password_hash,password))return res.status(401).json({error:"invalid_credentials"});if(!u.email_verified)return res.status(403).json({error:"email_verification_required"});const token=await new SignJWT({scope:["launcher:read","profile:read"]}).setProtectedHeader({alg:"HS256"}).setSubject(u.id).setIssuer(issuer).setAudience("my-services-api").setIssuedAt().setExpirationTime(process.env.ACCESS_TOKEN_TTL||"15m").sign(jwtKey);res.json({access_token:token,token_type:"Bearer",expires_in:900,user:{id:u.id,email:u.email,username:u.username}});}));
app.get("/v1/account/me",requireAuth,(req,res)=>res.json({user:{id:req.user.id,email:req.user.email,username:req.user.username,email_verified:req.user.email_verified}}));
app.use((err,_req,res,_next)=>{console.error("Request failed:",err.message);if(!res.headersSent)res.status(500).json({error:"internal_server_error"});});
const server=app.listen(port,()=>console.log("MY SERVICES Core listening on :"+port));
async function stop(){server.close(async()=>{await pool.end();process.exit(0);});}
process.on("SIGTERM",stop);process.on("SIGINT",stop);
