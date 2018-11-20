package controllers;

import play.mvc.Result;

import static play.mvc.Results.movedPermanently;

public class Untrailer {
    public Result untrail(String path) {
        return movedPermanently("/" + path);
    }
}
