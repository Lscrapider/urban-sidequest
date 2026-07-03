package com.urbansidequest.backend.domain.param;

import com.urbansidequest.backend.domain.enums.RouteInteractionReaction;

public class RouteInteractionParam {

    private Boolean favorite;

    private RouteInteractionReaction reaction;

    public Boolean getFavorite() {
        return this.favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public RouteInteractionReaction getReaction() {
        return this.reaction;
    }

    public void setReaction(RouteInteractionReaction reaction) {
        this.reaction = reaction;
    }
}
