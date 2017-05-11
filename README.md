# Minibus

A minimalistic message bus for Clojure with emphasis on loose coupling.

## Overview

The overall idea behind *Minibus* is to enable components of a system
to communicate via events in as decoupled way as possible. 

*Minibus* is built upon the
[Message Bus](https://msdn.microsoft.com/en-us/library/ff647328.aspx)
concept. There are multiple implementations of this idea, the most notable of them is probably 
[Immutant Messaging](http://immutant.org/documentation/current/apidoc/guide-messaging.html).
  
*Minibus* is different in a number of ways:

  * In-process by design - there is no notion of inter-process communication,
  * No queues or RPC-style callbacks - there is only pub/sub,
  * Specific to Clojure.

It also differs from the traditional Event Listener pattern in a way
that it externalizes the subscription machinery out of the event
emitters.

An important feature of *Minibus* is introspective transparency. It enables
developers to directly inspect and manipulate the bus state. This feature is
tremendously useful during the REPL-driven development cycle as it
allows for individual component restarts without a risk of driving the
system into some weird irreparable state, thus significantly reducing
the frequency of complete system restarts.

*Minibus* does not make any assumptions about a particular component
management framework, although it has been developed
with [Mount](https://github.com/tolitius/mount) in mind.

## Usage

Please don't use it yet, it's a proof of concept not suitable
for production use. It is more a quick hack than a real product.

Here is an example.

    user> (require '[clojure.core.async :as a]
                   '[minibus.core :as b])
    nil
    
Publish to a topic - nothing happens because nobody is listening.

    user> (b/publish! :world "hello")
    nil

Listening is a preferred way of using topics.

    user> (b/listen! :world #(println "***" %))
    [:world "df951be4-c906-4fe2-bb16-5647d173e5cd"]
    
    user> (b/publish! :world "hello")
    nil
    *** hello

Introspection:

    user> (b/topics)
    (:world)

    user> (b/subscriptions :topic :world)
    ([:world "df951be4-c906-4fe2-bb16-5647d173e5cd"])

It is possible to cancel all topic subscriptions at once:

    user> (b/unsubscribe-all! :topic :world)
    nil
    
    user> (b/subscriptions :topic :world)
    ()
    
    user> (b/publish! :world "hello") ;; nothing happens
    nil

The `listen!` call returns a subscription reference:

    user> (def sub (b/listen! :world #(println "***" %)))
    #user/sub
    
    user> sub
    [:world "f56b0bec-5f00-47df-94e8-8ff0bc82c101"]

    user> (b/unsubscribe! sub)
    nil
    
    user> (b/subscriptions :topic :world)
    ()
    
The endpoint of a subscription can be explicitly specified:

    user> (b/listen! :world #(println "***" %) :endpoint :alice)
    [:world :alice]
    
    user> (b/publish! :world "hello")
    nil
    *** hello

    user> (b/subscriptions)
    ([:world :alice])

    user> (b/unsubscribe! [:world :alice])
    nil

    user> (b/subscriptions)
    ()

Listeners are subscriptions without callbacks:

    user> (def sub (b/subscribe! :world :endpoint :manual))
    #user/sub

    user> (b/subscriptions)
    ([:world :manual])
    
Receiving a message from a subscription should happen withing a go block:

    user> (a/go (println "received" (b/receive! sub)))
    user> (b/publish! :world "hello")
    received hello

The same endpoint can be subscribed to different topics:

    user> (b/subscribe! :another :endpoint :manual)
    [:another :manual]

    user> (b/subscriptions)
    ([:world :manual] [:another :manual])
    
It is possible to cancel all endpoint's subscriptions at once:

    user> (b/subscribe! :world :endpoint :alice)
    [:another :manual]

    user> (b/subscriptions)
    ([:world :manual] [:world :alice] [:another :manual])
    
    user> (b/unsubscribe-all! :endpoint :manual)
    nil
    
    user> (b/subscriptions)
    ([:world :alice])
    
The bus is a component and can be restarted at any time:

    user> (b/restart!)
    true
    user> (b/subscriptions)
    ()


## License

Copyright © 2017 Mike Ivanov

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
