package com.google.gson;
public class GsonBuilder {
    public GsonBuilder setPrettyPrinting() { return this; }
    public Gson create() { return new Gson(); }
}